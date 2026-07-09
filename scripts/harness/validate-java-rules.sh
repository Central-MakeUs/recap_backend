#!/bin/bash
# RECAP 코드 규칙 검사 (docs/conventions/domain-design-principles.md 기반)
# 사용법: bash scripts/validate-java-rules.sh <java-file-path>
#
# 이 스크립트는 docs/conventions/domain-design-principles.md에 문서화된
# 규칙만 검사한다. 여기 없는 규칙은 아직 팀 합의가 없는 것이므로 추가하지 않는다.

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
    IS_ENTITY=$(grep -n '^@Entity' "$FILE")
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
        /^\s*(public|protected|private)(\s+static)?\s+[A-Za-z_][A-Za-z0-9_<>\[\],. ]*\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\(/ {
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
# 결과
# ──────────────────────────────────────────────
if [[ $ERRORS -gt 0 ]]; then
    echo "⛔ 규칙 위반 ${ERRORS}건 — 수정 필요"
    exit 1
else
    echo "✅ 규칙 검사 통과"
fi