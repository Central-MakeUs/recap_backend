# LLD-0021: 월간 AI 분석 사용량 제한

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-08-12 |
| 관련 | LLD-0016(AI 동의 관리), LLD-0012(월/기간 계산 버그 사례) |

## 맥락 (Context)

무료/유료 구분의 기준선이 될 월간 AI 분석 사용량 제한(월 20장)을
도입한다. 한도 초과 시 정리 요청을 거부하고, 사용자가 이번 달
사용량을 조회할 수 있는 API를 제공한다.

## 결정 (Decision)

### 카운트 방식 — 별도 카운터 없이 InfoCard 실측 COUNT

새 카운터 필드/테이블을 만들지 않는다. `InfoCard.createdAt` 기준
이번 달(달력 월) 생성된 InfoCard 개수를 그때그때 COUNT 쿼리로
집계한다. 실패한 이미지는 InfoCard 자체가 생성되지 않으므로,
자연스럽게 "성공한 것만" 정직하게 집계된다 — 별도 로직 불필요.

```java
// InfoCardRepository.java
long countByUserAndCreatedAtAfter(User user, Instant since);
```

### 월 경계 계산 — `LocalDate`로 계산 후 `Instant` 변환 (버그 회피)

**`Instant.now().minus(Period.ofMonths(N))` 방식은 쓰지 않는다.**
`Instant`는 날짜 기반 단위(YEARS/MONTHS)를 지원하지 않아
`UnsupportedTemporalTypeException`을 던진다 — LLD-0012/LLD-0019에서
동일한 문제를 실제로 겪었다. 이번엔 `LocalDate`가 네이티브로
지원하는 `withDayOfMonth()`/`plusMonths()`로 먼저 계산을 끝내고,
마지막에만 `Instant`로 변환한다.

```java
private Instant monthStart() {
    return LocalDate.now(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
}

private Instant resetAt() {
    return LocalDate.now(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .plusMonths(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
}
```

### 리셋 기준 — 달력 월 (매월 1일 00:00 UTC)

가입일 기준 30일 롤링 윈도우는 채택하지 않는다 — 사용자마다 리셋
시점이 달라져 이해하기 어렵고 계산도 복잡해진다.

### 거부 방식 — 전체 거부 (all-or-nothing)

기존 "1회 20장 초과 시 전체 거부" 패턴과 일관되게, 이번 달 사용량
+ 요청 imageKeys 개수가 한도를 초과하면 요청 전체를 거부한다.
일부만 처리하는 방식은 도입하지 않는다.

### 에러 코드 — 429 (403이 아님)

```
ErrorCode.MONTHLY_USAGE_LIMIT_EXCEEDED, HTTP 429 (Too Many Requests)
```

기존 `AI_CONSENT_REQUIRED`(403)와 의미상 구분한다 — 403은 "권한
자체가 없음", 429는 "지금은 안 되지만 다음 달엔 다시 가능한 사용량
제한"이라 HTTP 표준 의미에 더 정확히 부합한다.

### `UsageService` 신규 (기존 서비스에 통합하지 않음)

한도 체크 로직은 `OrganizeService`(정리 요청 시 체크)와 신규 조회
API(`GET /users/me/usage`) 양쪽에서 쓰인다. `ConsentService`를
분리했던 것과 같은 이유로, 어느 한쪽에 종속시키지 않고 독립
서비스로 둔다.

```java
@Service
@RequiredArgsConstructor
public class UsageService {

    private static final int MONTHLY_LIMIT = 20;

    private final UserRepository userRepository;
    private final InfoCardRepository infoCardRepository;

    public UsageResponse getUsage(Long userId) {
        User user = userRepository.getReferenceById(userId);
        int usedCount = countThisMonth(user);
        return UsageResponse.of(usedCount, MONTHLY_LIMIT, resetAt());
    }

    public void checkLimit(Long userId, int requestCount) {
        User user = userRepository.getReferenceById(userId);
        int usedCount = countThisMonth(user);
        if (usedCount + requestCount > MONTHLY_LIMIT) {
            throw new BusinessException(ErrorCode.MONTHLY_USAGE_LIMIT_EXCEEDED);
        }
    }

    private int countThisMonth(User user) {
        return (int) infoCardRepository.countByUserAndCreatedAtAfter(user, monthStart());
    }

    private Instant monthStart() { ... } // 위와 동일
    private Instant resetAt() { ... }    // 위와 동일
}
```

`MONTHLY_LIMIT`은 상수로 관리해, PM의 검증 절차(50명 실사용
데이터 기준 조정)에 따라 값만 쉽게 바꿀 수 있게 한다.

### 응답 DTO

```java
public record UsageResponse(int usedCount, int limit, int remaining, Instant resetAt) {
    public static UsageResponse of(int usedCount, int limit, Instant resetAt) {
        int remaining = Math.max(0, limit - usedCount);
        return new UsageResponse(usedCount, limit, remaining, resetAt);
    }
}
```

### API

```
GET /api/v1/users/me/usage
```

```json
{ "usedCount": 18, "limit": 20, "remaining": 2, "resetAt": "2026-09-01T00:00:00Z" }
```

기존 `UserController`/`UserApiDocs`에 추가한다(`/users` 리소스
하위, 기존 판단 근거와 동일).

### `OrganizeService.organize()` 통합 — 체크 순서

```java
public OrganizeResponse organize(Long userId, List<String> imageKeys) {
    if (!consentService.hasActiveConsent(userId)) {
        throw new BusinessException(ErrorCode.AI_CONSENT_REQUIRED);
    }
    if (imageKeys.size() > MAX_IMAGES_PER_REQUEST) { // 기존 1회 20장 제한, 그대로 유지
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    usageService.checkLimit(userId, imageKeys.size()); // 신규 삽입 위치
    // 기존 소유권 검증(imageKeys의 objectKey가 본인 것인지) 이어서 진행
    ...
}
```

순서: 동의 확인 → 1회 요청 개수 제한(기존) → 월 한도(신규) →
소유권 검증(기존). 앞 단계에서 걸리면 뒤 단계 검증은 수행하지
않는다(기존 `AI_CONSENT_REQUIRED` 테스트와 동일한 원칙).

### 동시성 — 별도 락 불필요

기존 "PROCESSING 배치가 있으면 `ORGANIZE_IN_PROGRESS`로 막는다"는
규칙(`organizeBatchRepository.existsByUserAndStatus`)으로 이미 한
유저가 동시에 여러 정리 요청을 보낼 수 없는 구조가 갖춰져 있다.
`checkLimit()`과 실제 InfoCard 생성 사이에 같은 유저가 끼어들
여지가 없어 TOCTOU 레이스가 생기지 않는다.

## 고려한 대안 (Considered Options)

1. **별도 카운터 필드로 사용량 관리 (기각)** — InfoCard 실측
   COUNT로 항상 정확한 값을 얻을 수 있어, 카운터 증감 로직의
   불일치 위험(예: 실패 시 롤백 누락)을 원천적으로 피할 수 있음.
2. **가입일 기준 30일 롤링 윈도우 (기각)** — 계산 복잡도, 사용자
   이해도 모두 달력 월보다 불리함.
3. **한도 초과 시 403 (기각)** — 의미상 "일시적 사용량 제한"에는
   429가 더 정확함.
4. **일부만 처리하는 방식(예: 남은 한도만큼만 처리) (기각)** —
   기존 1회 요청 제한 정책(전체 거부)과 일관성이 깨짐, 사용자가
   "몇 장이 처리됐는지" 혼란스러울 수 있음.

## 결과 (Consequences)

### 긍정
- 별도 상태 관리 없이 항상 정확한 실측값 기반 판단.
- `LocalDate` 기반 계산으로 이전에 실제로 겪었던 월/나노초 계산
  버그 유형을 원천 차단.
- 한도값이 상수라 향후 조정이 코드 한 줄 변경으로 끝남.

### 부정 / 트레이드오프
- 매 정리 요청·조회마다 COUNT 쿼리가 실행됨 — 지금 규모에선
  무시할 수준이나, 유저/데이터가 크게 늘어나면 인덱스 확인 필요.

## 후속 / 미결정

- [ ] 탈퇴 후 재가입을 통한 한도 우회는 이번 범위에서 방지하지
      않는다 — 별도 논의(재가입 어뷰징 방지) 결과에 따름
- [ ] `createdAt`에 인덱스가 없다면, 유저 증가 시 `(user_id,
      created_at)` 복합 인덱스 추가 검토
