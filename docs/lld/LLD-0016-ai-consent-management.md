# LLD-0016: AI 분석 전송 동의 관리

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-29 |
| 관련 | [ADR-0015](../adr/ADR-0015-user-withdrawal-hybrid-deletion.md), [LLD-0015](LLD-0015-account-data-management.md), [LLD-0002](LLD-0002-capture-upload-organize-explore.md) |

## 맥락 (Context)

08-04(데이터 관리) 화면 개정으로 "AI 데이터 전송 동의" 상태 표시 및
철회 기능(M-08-01)이 추가됐다. 철회 시 정리(이미지 분석) 기능
접근을 막아야 한다.

**이건 RBAC(역할 기반 접근 제어)가 아니라 단순 동의 여부 게이트다.**
RECAP은 지금까지 Spring Security를 인증(로그인 여부)에만 써왔고
인가(권한별 접근 제어) 체계가 없다. 제출을 코앞에 두고 새 패러다임을
도입하는 리스크를 피하기 위해, 서비스 레이어의 도메인 체크 하나로
구현한다.

## 결정 (Decision)

### 저장 방식 — 이력 엔티티, 상태 필드 아님

동의/철회할 때마다 새 row를 추가하는 `ConsentHistory`로 기록한다.
화면 설계서의 "철회 처리(철회 일시 기록)"라는 표현이 단발성 상태가
아니라 이력 기록을 의도하고 있고, 추후 감사(audit) 대응 여지를
남긴다.

```java
public enum ConsentAction { GIVEN, WITHDRAWN }

@Entity
@Table(name = "consent_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ConsentAction action;

    public static ConsentHistory give(User user) {
        ConsentHistory history = new ConsentHistory();
        history.user = user;
        history.action = ConsentAction.GIVEN;
        return history;
    }

    public static ConsentHistory withdraw(User user) {
        ConsentHistory history = new ConsentHistory();
        history.user = user;
        history.action = ConsentAction.WITHDRAWN;
        return history;
    }
}
```

**`consentType` 필드는 넣지 않는다** — 지금 동의 유형이 하나뿐이라
확장을 미리 대비한 필드는 YAGNI 위반. 필요해지면 그때 추가한다.

**별도 `actionAt` 필드도 넣지 않는다** — 이 엔티티는 생성 후 절대
수정되지 않으므로(update 자체가 없음), `BaseTimeEntity.createdAt`이
곧 행위 발생 시각과 정확히 일치한다.

**재동의/재철회에 대한 예외 처리 안 함**: M-03-01(동의)/M-08-01(철회)
둘 다 화면 흐름상 이미 그 상태면 해당 버튼 자체가 안 뜨도록 설계되어
있어, 실제로 중복 호출될 경로가 없다. 혹시 호출되어도 이력에 중복
row가 하나 더 쌓이는 정도로 무해하다 — 신고 중복 방지(DB 유니크
제약)처럼 데이터 정합성이 걸린 문제가 아니라 새 에러 코드를 추가하지
않는다.

### `ConsentService` 신규 분리 (기존 `UserService`에 통합하지 않음)

동의 로직은 두 곳에서 쓰인다 — `UserController`(상태조회/동의/철회
API)와 `OrganizeService`(정리 시작 시 게이트 체크). `UserService`에
넣으면 `OrganizeService`가 회원탈퇴·계정정보 등 무관한 로직까지
포함된 `UserService` 전체에 의존하게 된다. 별도 `ConsentService`로
분리해 `OrganizeService`가 필요한 것만 의존하게 한다.

```java
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final UserRepository userRepository;
    private final ConsentHistoryRepository consentHistoryRepository;

    public ConsentStatusResponse getStatus(Long userId) {
        User user = getUser(userId);
        return ConsentStatusResponse.from(latestHistory(user));
    }

    @Transactional
    public void give(Long userId) {
        User user = getUser(userId);
        consentHistoryRepository.save(ConsentHistory.give(user));
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = getUser(userId);
        consentHistoryRepository.save(ConsentHistory.withdraw(user));
    }

    public boolean hasActiveConsent(Long userId) {
        User user = getUser(userId);
        return latestHistory(user)
                .map(h -> h.getAction() == ConsentAction.GIVEN)
                .orElse(false);
    }

    private Optional<ConsentHistory> latestHistory(User user) {
        return consentHistoryRepository.findFirstByUserOrderByCreatedAtDesc(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
```

### 응답 DTO

```java
public record ConsentStatusResponse(boolean consented, Instant consentedAt) {
    public static ConsentStatusResponse from(Optional<ConsentHistory> latest) {
        if (latest.isEmpty() || latest.get().getAction() == ConsentAction.WITHDRAWN) {
            return new ConsentStatusResponse(false, null);
        }
        return new ConsentStatusResponse(true, latest.get().getCreatedAt());
    }
}
```

`consented=false`일 때 `consentedAt`은 `null`(08-04 화면의 "동의하지
않음" 상태는 날짜를 안 보여줌).

### 신규 Repository 메서드

```java
Optional<ConsentHistory> findFirstByUserOrderByCreatedAtDesc(User user);
```

### API

```
GET    /api/v1/users/me/consent    → ConsentStatusResponse (200)
POST   /api/v1/users/me/consent    → 204 (동의 기록)
DELETE /api/v1/users/me/consent    → 204 (철회 기록)
```

기존 `UserController`/`UserApiDocs`에 추가한다(리소스 경로가
`/users` 하위이므로, `ConsentController`를 새로 만들지 않음 — 다만
내부적으로는 신규 `ConsentService`를 호출).

### `OrganizeService.organize()` 진입부 게이트

```java
public OrganizeResponse organize(Long userId, List<String> imageKeys) {
    if (!consentService.hasActiveConsent(userId)) {
        throw new BusinessException(ErrorCode.AI_CONSENT_REQUIRED);
    }
    // 기존 imageKeys 검증, ORGANIZE_IN_PROGRESS 체크 등은 이 체크 다음
    ...
}
```

동의 체크를 다른 검증(이미지 키 개수, 진행 중 배치 여부)보다 먼저
수행한다 — 동의가 없으면 나머지 검증을 할 이유가 없다.

### 에러 코드

| 상황 | ErrorCode | HTTP |
| --- | --- | --- |
| 미동의 상태에서 정리 시도 | `AI_CONSENT_REQUIRED`(신규) | 403 |

403을 쓰는 이유: 요청 자체는 유효하나(인증 정상, 형식 정상) 동의가
없어 접근을 거부하는 것이라 400(입력값 오류)보다 의미상 정확하다.

### 패키지 위치

`ConsentHistory`(도메인), `ConsentHistoryRepository`(리포지토리),
`ConsentService`(서비스)는 `cmc.recap.user` 하위에 둔다 —
`InfoCard`를 참조하지 않고 `User`만 참조하는 순수 유저 부속
개념이라(신고 기능처럼 `InfoCard`를 참조해 별도 최상위 패키지가
필요했던 경우와 다름), 새 최상위 패키지를 만들지 않는다.

### 기존 계정 처리 — 별도 마이그레이션 불필요

신규 테이블이라 기본값이 "이력 없음"(= 미동의) 상태로 시작한다.
기존 테스트 계정도 배포 후 첫 정리 시도 때 자연스럽게
`AI_CONSENT_REQUIRED`를 받고 M-03-01을 다시 보게 된다 — 이게 이
기능이 의도한 정상 동작이다.

### 회원탈퇴/데이터 삭제와의 관계 — 별도 처리 불필요

`ConsentHistory`는 `User`를 참조하는 별도 테이블이라, 회원탈퇴
(`User` row 익명화 유지, ADR-0015)나 데이터 삭제(캡처만 삭제,
LLD-0015) 어느 쪽과도 충돌하지 않는다.

## 고려한 대안 (Considered Options)

1. **RBAC(Spring Security Role/Authority) 도입 (기각)** — 지금
   RECAP은 인가 체계가 전혀 없고, 필요한 건 boolean 하나 체크뿐.
   제출 임박 시점에 새 패러다임 도입은 리스크 대비 이득이 없음.
2. **`consentType` 필드 선제 추가 (기각)** — 동의 유형이 하나뿐,
   YAGNI.
3. **`ConsentService`를 `UserService`에 통합 (기각)** —
   `OrganizeService`가 무관한 로직까지 포함한 `UserService` 전체에
   의존하게 됨.
4. **재동의/재철회 시 명시적 예외 처리 (기각)** — 화면 흐름상 도달
   불가능한 경로, 도달해도 무해함.

## 결과 (Consequences)

### 긍정
- 인가 체계 없이도 필요한 게이트 기능을 안전하게 구현.
- `ConsentService` 분리로 `OrganizeService`의 의존성이 깔끔하게
  유지됨.

### 부정 / 트레이드오프
- 없음.

## 후속 / 미결정

- 없음.
