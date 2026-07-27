#!/bin/bash
# RECAP 코드 규칙 검사 (docs/conventions/domain-design-principles.md 기반)
# 사용법: bash scripts/validate-java-rules.sh <java-file-path>
#
# 이 스크립트는 docs/conventions/domain-design-principles.md에 문서화된
# 규칙만 검사한다. 여기 없는 규칙은 아직 팀 합의가 없는 것이므로 추가하지 않는다.
#
# 참고: 디미터의 법칙, SRP는 이 스크립트(정규식/패턴 매칭)로 검증 불가능한
# 설계 판단 영역이라 자동화하지 않는다 — review 스킬의 사람(또는 리뷰
# 에이전트) 판단 체크리스트 항목으로 다룬다.

FILE="$1"
if [[ ! -f "$FILE" ]]; then exit 0; fi

BASENAME=$(basename "$FILE")
ERRORS=0

echo "[HARNESS] 규칙 검사: ${FILE#*/recap/}"

# ──────────────────────────────────────────────
# 규칙 1: DTO 직접 생성 금지 (Service/Facade)
# → domain-design-principles.md #5
# ──────────────────────────────────────────────
if [[ "$BASENAME" == *Service* || "$BASENAME" == *Facade* ]]; then
    DTO_NEW=$(grep -nE 'new [A-Z][a-zA-Z]*(Response|Request|Dto)\(' "$FILE" \
        | grep -v '^\s*//')
    if [[ -n "$DTO_NEW" ]]; then
        echo "❌ [규칙1] DTO 직접 생성 금지 → DTO에 from()/of() 정적 팩토리 추가"
        echo "   from(Entity) : 엔티티 변환 시 / of(값...) : 값 조합 시"
        echo "$DTO_NEW" | sed 's/^/   /'
        ((ERRORS++))
    fi
fi

# ──────────────────────────────────────────────
# 규칙 2: Controller → Repository 직접 접근 금지
# → domain-design-principles.md #6
# ──────────────────────────────────────────────
if [[ "$FILE" == *"/controller/"* ]]; then
    REPO_IMPORT=$(grep -n 'import.*\.repository\.' "$FILE" | grep -v '^\s*//')
    if [[ -n "$REPO_IMPORT" ]]; then
        echo "❌ [규칙2] Controller에서 Repository 직접 import 금지 (계층 분리 원칙)"
        echo "$REPO_IMPORT" | sed 's/^/   /'
        ((ERRORS++))
    fi
fi

# ──────────────────────────────────────────────
# 규칙 3: 엔티티에 setter 금지
# → domain-design-principles.md #2
# ──────────────────────────────────────────────
if [[ "$FILE" == *"/domain/"* ]]; then
    IS_ENTITY=$(grep -nE '^@Entity([[:space:]]|$)' "$FILE")
    if [[ -n "$IS_ENTITY" ]]; then
        SETTER=$(grep -nE '(public|protected)\s+void\s+set[A-Z]' "$FILE" | grep -v '^\s*//')
        if [[ -n "$SETTER" ]]; then
            echo "❌ [규칙3] 엔티티에 setter 금지 → 의도가 드러나는 이름의 메서드로 변경"
            echo "$SETTER" | sed 's/^/   /'
            ((ERRORS++))
        fi
    fi
fi

# ──────────────────────────────────────────────
# 규칙 4: 도메인/서비스 로직에서 원시 예외 금지
# → domain-design-principles.md #1, #4
# ──────────────────────────────────────────────
if [[ "$FILE" == *"/domain/"* || "$FILE" == *"/service/"* ]]; then
    RAW_EXCEPTION=$(grep -nE 'throw new (IllegalStateException|IllegalArgumentException)\(' "$FILE" \
        | grep -v '^\s*//')
    if [[ -n "$RAW_EXCEPTION" ]]; then
        echo "❌ [규칙4] 원시 예외 금지 → BusinessException(ErrorCode)로 교체"
        echo "$RAW_EXCEPTION" | sed 's/^/   /'
        ((ERRORS++))
    fi
fi

# ──────────────────────────────────────────────
# 규칙 5: JPA AttributeConverter에 생성자 DI 금지 (ADR-0005)
# → 실제로 CardSummaryConverter에서 ObjectMapper 기동 실패를 겪은 이력
# ──────────────────────────────────────────────
if [[ "$BASENAME" == *Converter.java ]]; then
    IS_CONVERTER=$(grep -n '^@Converter' "$FILE")
    if [[ -n "$IS_CONVERTER" ]]; then
        CTOR_WITH_ARGS=$(grep -nE "public ${BASENAME%.java}\([^)]+\)" "$FILE")
        if [[ -n "$CTOR_WITH_ARGS" ]]; then
            echo "❌ [규칙5] AttributeConverter는 무인자 생성자만 허용 (ADR-0005)"
            echo "   필요한 협력 객체는 static 필드로 직접 소유할 것"
            echo "$CTOR_WITH_ARGS" | sed 's/^/   /'
            ((ERRORS++))
        fi
    fi
fi

# ──────────────────────────────────────────────
# 규칙 6: 메서드 배치 순서 (private은 맨 아래로)
# → domain-design-principles.md #7
# 완벽한 파서가 아니라 휴리스틱이다: "private 메서드가 한 번이라도
# 나온 뒤에 public 메서드가 다시 나오면" 위반으로 본다.
# ──────────────────────────────────────────────
if [[ "$FILE" == *"/domain/"* || "$FILE" == *"/service/"* ]]; then
    ORDER_VIOLATION=$(awk '
        /^[ \t]*(public|protected|private)([ \t]+static)?[ \t]+[A-Za-z_][A-Za-z0-9_<>\[\],. ]*[ \t]+[a-zA-Z_][a-zA-Z0-9_]*[ \t]*\(/ {
            if ($0 ~ /private/) {
                seen_private = 1
            } else if ($0 ~ /public/ && seen_private == 1) {
                print NR": "$0" ← private 메서드 이후에 public 메서드 발견 (private은 맨 아래로)"
            }
        }
    ' "$FILE")
    if [[ -n "$ORDER_VIOLATION" ]]; then
        echo "⚠️  [규칙6] 메서드 배치 순서 의심 (휴리스틱 — 오탐 가능, 직접 확인 필요)"
        echo "$ORDER_VIOLATION" | sed 's/^/   /'
    fi
fi

# ──────────────────────────────────────────────
# 규칙 7: 본문 코드에 FQN(전체 경로) 인라인 참조 금지
# → docs/swagger/api-spec-guide.md #8
# import 없이 cmc.recap.* 전체 경로를 코드 본문에 그대로 쓰면 경고.
# 이름 충돌(ApiResponse 등)로 불가피한 경우만 예외로 허용되며,
# 그 경우 이 파일의 리뷰어가 직접 판단한다 (자동 면제 처리는 하지 않음).
# ──────────────────────────────────────────────
FQN_INLINE=$(grep -nE '(^|[^."a-zA-Z0-9_])cmc\.recap\.[a-z][a-zA-Z0-9_.]*\.[A-Z][a-zA-Z0-9_]*' "$FILE" \
    | grep -vE '^[0-9]+:\s*import ' | grep -vE '^[0-9]+:\s*package ' | grep -vE '^[0-9]+:\s*//')
if [[ -n "$FQN_INLINE" ]]; then
    echo "⚠️  [규칙7] 본문에 FQN 인라인 참조 의심 (휴리스틱 — 이름 충돌로 인한"
    echo "   의도적 예외라면 무시해도 됨. api-spec-guide.md #8 기준 확인)"
    echo "$FQN_INLINE" | sed 's/^/   /'
fi

# ──────────────────────────────────────────────
# 규칙 8: @Entity는 domain 패키지 안에만 위치
# → 패키지 구조 관례. 엔티티가 다른 계층 패키지에 섞이면 계층 경계가
# 흐려짐.
# ──────────────────────────────────────────────
IS_ENTITY_FILE=$(grep -nE '^@Entity([[:space:]]|$)' "$FILE")
if [[ -n "$IS_ENTITY_FILE" && "$FILE" != *"/domain/"* ]]; then
    echo "❌ [규칙8] @Entity는 domain 패키지 안에만 위치해야 함"
    echo "   현재 위치: ${FILE#*/recap/}"
    ((ERRORS++))
fi

# ──────────────────────────────────────────────
# 규칙 9: @Async 메서드에 @Transactional을 직접 붙이지 말 것
# → 실제로 OrganizeService에서 겪은 문제(비동기 디스패치가 트랜잭션
# 커밋보다 먼저 일어나는 레이스)와 같은 계열. @Async와 @Transactional을
# 같은 메서드에 같이 붙이면 트랜잭션 컨텍스트가 비동기 스레드로 제대로
# 전달되지 않을 수 있음. 별도 빈의 @Transactional 메서드를 호출하는
# 구조로 분리할 것(ImageAnalysisTaskRunner → OrganizeService.completeImage()
# 패턴 참고).
# 휴리스틱: 같은 메서드 선언 앞 4줄 이내에 두 애너테이션이 함께
# 나타나면 위반으로 본다.
# ──────────────────────────────────────────────
if [[ "$FILE" == *"/service/"* ]]; then
    ASYNC_TX_TOGETHER=$(awk '
        /@Async/ { async_at = NR }
        /@Transactional/ { tx_at = NR }
        /^[ \t]*(public|protected|private)([ \t]+static)?[ \t]+[A-Za-z_][A-Za-z0-9_<>\[\],. ]*[ \t]+[a-zA-Z_][a-zA-Z0-9_]*[ \t]*\(/ {
            if (async_at > 0 && tx_at > 0 && (NR - async_at <= 4) && (NR - tx_at <= 4)) {
                print NR": "$0" ← 같은 메서드에 @Async+@Transactional 동시 적용 의심"
            }
        }
    ' "$FILE")
    if [[ -n "$ASYNC_TX_TOGETHER" ]]; then
        echo "⚠️  [규칙9] @Async 메서드에 @Transactional 동시 적용 의심 (휴리스틱)"
        echo "   별도 빈의 @Transactional 메서드를 호출하는 구조로 분리 권장"
        echo "$ASYNC_TX_TOGETHER" | sed 's/^/   /'
    fi
fi

# ──────────────────────────────────────────────
# 규칙 10: @Transactional을 private/protected 메서드에 붙이지 말 것
# → Spring @Transactional은 프록시 기반이라 private/protected
# 메서드엔 조용히 적용되지 않는다(가장 흔한 Spring 함정 중 하나).
# 휴리스틱: 메서드 선언 앞 3줄 이내에 @Transactional이 있는데
# private/protected면 위반으로 본다.
# ──────────────────────────────────────────────
if [[ "$FILE" == *"/service/"* ]]; then
    TX_ON_PRIVATE=$(awk '
        /@Transactional/ { tx_at = NR }
        /^[ \t]*(private|protected)([ \t]+static)?[ \t]+[A-Za-z_][A-Za-z0-9_<>\[\],. ]*[ \t]+[a-zA-Z_][a-zA-Z0-9_]*[ \t]*\(/ {
            if (tx_at > 0 && (NR - tx_at <= 3)) {
                print NR": "$0" ← @Transactional이 private/protected 메서드에 적용됨(프록시가 안 먹음)"
            }
        }
    ' "$FILE")
    if [[ -n "$TX_ON_PRIVATE" ]]; then
        echo "❌ [규칙10] @Transactional은 private/protected 메서드에서 동작하지 않음"
        echo "$TX_ON_PRIVATE" | sed 's/^/   /'
        ((ERRORS++))
    fi
fi

# ──────────────────────────────────────────────
# 규칙 11: 필드 주입(@Autowired 필드) 금지 → 생성자 주입만 허용
# → 지금까지 전부 생성자 주입으로 지켜온 관례를 규칙으로 고정.
# 테스트 클래스(*Test.java)는 @WebMvcTest/@SpringBootTest 등에서
# MockMvc·JwtProvider류를 필드 주입받는 것이 관용적이라 이 규칙에서 제외.
# 휴리스틱: @Autowired 바로 다음 줄(1~2줄 이내)에 필드 선언이 오면 위반.
# ──────────────────────────────────────────────
if [[ "$BASENAME" != *Test.java ]]; then
    FIELD_INJECTION=$(awk '
        /@Autowired/ { autowired_at = NR }
        /^[ \t]*(private|protected)[ \t]+[A-Za-z_][A-Za-z0-9_<>\[\], ]*[ \t]+[a-zA-Z_][a-zA-Z0-9_]*[ \t]*;/ {
            if (autowired_at > 0 && (NR - autowired_at <= 2)) {
                print NR": "$0" ← 필드 주입 금지, 생성자 주입 사용"
            }
        }
    ' "$FILE")
    if [[ -n "$FIELD_INJECTION" ]]; then
        echo "❌ [규칙11] 필드 주입 금지 → 생성자 주입으로 변경"
        echo "$FIELD_INJECTION" | sed 's/^/   /'
        ((ERRORS++))
    fi
fi

# ──────────────────────────────────────────────
# 규칙 12: System.out/err.print*, printStackTrace() 금지
# → 로거(log.info/error 등) 사용이 이미 확립된 관례. 디버깅용
# println이 실수로 커밋되는 것을 방지.
# ──────────────────────────────────────────────
PRINTLN=$(grep -nE '(System\.(out|err)\.print|\.printStackTrace\(\))' "$FILE" \
    | grep -v '^\s*//')
if [[ -n "$PRINTLN" ]]; then
    echo "❌ [규칙12] System.out/err.print*, printStackTrace() 금지 → 로거 사용"
    echo "$PRINTLN" | sed 's/^/   /'
    ((ERRORS++))
fi

# ──────────────────────────────────────────────
# 규칙 13: TODO/FIXME 주석 잔존 (경고, 차단 아님)
# → 배포 전 급하게 작업하다 남긴 TODO가 그대로 나가는 것을 방지.
# ──────────────────────────────────────────────
TODO_LEFT=$(grep -nE '//\s*(TODO|FIXME)' "$FILE")
if [[ -n "$TODO_LEFT" ]]; then
    echo "⚠️  [규칙13] TODO/FIXME 주석이 남아있음 (배포 전 확인)"
    echo "$TODO_LEFT" | sed 's/^/   /'
fi

# ──────────────────────────────────────────────
# 규칙 14: 삼항 연산자 전면 금지
# → 팀 결정으로 전면 금지(단순/중첩 구분 없이). if-else 또는 별도
# 메서드로 의도를 명확히 드러낼 것.
# 주의: "?"와 ":" 둘 다 요구하는 조건은 반드시 하나의 정규식 안에서
# 검사해야 한다. grep -n으로 줄번호를 먼저 붙인 뒤 별도 파이프로
# ':' 유무를 검사하면, 줄번호 자체의 콜론(예: "34:") 때문에 필터가
# 무의미해진다(실제로 이 버그로 오탐 발생했던 이력 있음) — 반드시
# 아래처럼 하나의 -E 패턴 안에서 "? 다음에 : "를 요구할 것.
# 휴리스틱 한계: 제네릭 와일드카드(<?>, <? extends X>)는 제외하지만,
# 문자열 리터럴 안의 "?"와 ":" 조합(예: URL 쿼리스트링), Javadoc
# 안의 예시 코드까지는 완벽히 걸러내지 못할 수 있다. 오탐이면
# 리뷰어가 무시해도 된다.
# 여러 줄 삼항연산자도 잡는다. 실제 코드 스타일은 연산자를 줄 맨 앞에
# 두는 방식이다(조건식 줄 다음에 "? 값", 그다음 줄에 ": 값") —
# 한 줄짜리 grep만으로는 "?"와 ":"가 서로 다른 줄에 있으면 놓치기
# 때문에, awk로 직전에 "?"로 시작하는 줄을 봤는지 상태를 들고 가다가
# ":"로 시작하는 줄을 만나면 그 쌍을 위반으로 본다.
# ──────────────────────────────────────────────
TERNARY=$(awk '
    function is_comment(s) {
        return (s ~ /^\/\// || s ~ /^\*/)
    }
    function is_generic_wildcard(s) {
        return (s ~ /^\?[ \t]*[,>)]/ || s ~ /^\?[ \t]+(extends|super)[ \t]/)
    }
    {
        stripped = $0
        gsub(/^[ \t]*/, "", stripped)
        gsub(/[ \t]*$/, "", stripped)
    }
    {
        if (!is_comment(stripped)) {
            if ($0 ~ /\?[^?:]*:/ && $0 !~ /<[ \t]*\?/) {
                print NR": "$0" ← 삼항 연산자 의심(같은 줄)"
            } else if (prev_q_line > 0 && (NR - prev_q_line <= 3) && stripped ~ /^:/) {
                print prev_q_line": "prev_q_text" ← 삼항 연산자 의심(여러 줄, "NR"번째 줄의 : 와 짝)"
            }
        }
        if (!is_comment(stripped) && stripped ~ /^\?/ && !is_generic_wildcard(stripped)) {
            prev_q_line = NR
            prev_q_text = $0
        } else if (!is_comment(stripped) && stripped != "") {
            prev_q_line = 0
        }
    }
' "$FILE")
if [[ -n "$TERNARY" ]]; then
    echo "❌ [규칙14] 삼항 연산자 전면 금지 → if-else 또는 별도 메서드로 대체"
    echo "   (제네릭 와일드카드 <?>는 제외됨. 문자열/Javadoc 오탐 가능성 있음,"
    echo "   실제 삼항 연산자가 아니면 무시해도 됨)"
    echo "$TERNARY" | sed 's/^/   /'
    ((ERRORS++))
fi

# ──────────────────────────────────────────────
# 결과
# ──────────────────────────────────────────────
if [[ $ERRORS -gt 0 ]]; then
    echo "⛔ 규칙 위반 ${ERRORS}건 — 수정 필요"
    exit 1
else
    echo "✅ 규칙 검사 통과"
fi