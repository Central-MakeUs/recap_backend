# LLD-0009: 회원탈퇴 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-28 |
| 관련 | [ADR-0015](../adr/ADR-0015-user-withdrawal-hybrid-deletion.md), [LLD-0005](LLD-0005-capture-detail-favorite-delete.md) |

## 맥락 (Context)

ADR-0015에서 확정한 하이브리드 삭제 정책을 실제 API로 구현한다.

## 결정 (Decision)

### User 엔티티 변경

```java
@Column(name = "withdrawn", nullable = false)
private boolean withdrawn; // 기본값 false

public void withdraw() {
    if (this.withdrawn) {
        throw new BusinessException(ErrorCode.ALREADY_WITHDRAWN);
    }
    this.deviceId = "WITHDRAWN-" + UUID.randomUUID();
    this.oauthProvider = null;
    this.oauthId = null;
    this.email = null;
    this.fcmToken = null;
    this.withdrawn = true;
}
```

이미 탈퇴한 유저가 다시 탈퇴를 요청하면 조용히 무시하지 않고 예외로
막는다(`domain-design-principles.md` #2 — 이미 확립된 원칙과 일관).
신규 `ErrorCode.ALREADY_WITHDRAWN`(409, CONFLICT — `ORGANIZE_IN_PROGRESS`와
같은 "요청은 유효하나 현재 상태와 충돌" 범주) 추가 필요.

`deviceId`는 컬럼이 `NOT NULL UNIQUE`라 `null` 불가 — 복구 불가능한
랜덤 값으로 덮어써서 원래 값을 알아낼 수 없게 한다. `oauthProvider`/
`oauthId`/`email`/`fcmToken`은 이미 nullable이라 그대로 `null` 처리.

### 신규 Repository 메서드

```java
// InfoCardRepository
List<InfoCard> findByUser(User user);

// RefreshTokenRepository
void deleteByUser(User user); // 또는 findByUser 후 반복 revoke, 구현 시 판단
```

### UserService.withdraw(Long userId) — 처리 순서

```
1. User 조회
2. infoCardRepository.findByUser(user)로 전체 InfoCard 조회
3. 각 InfoCard의 원본 S3 오브젝트를 배치 삭제(DeleteObjectsRequest)
4. infoCardRepository.deleteAll(cards)로 DB 일괄 삭제
5. refreshTokenRepository.deleteByUser(user)로 RefreshToken 전체 삭제
6. user.withdraw() 호출(익명화 + 플래그)
```

**S3 배치 삭제 로직 재사용에 대한 판단**: `OrganizeService.deleteCards()`
가 이미 같은 메커니즘(S3 배치 삭제 → DB 일괄 삭제)을 구현하고 있다.
이번엔 **기존 코드를 리팩터링해서 공유 컴포넌트로 뽑아내지 않고,
`UserService`에 유사한 로직을 별도로 작성**한다 — 일정이 촉박한
시점에 이미 동작 중인 `OrganizeService`를 건드리는 건 불필요한 리스크다.
다만 다음 이슈(다중 캡처 삭제 API)에서 같은 패턴이 세 번째로 필요해
지면, 그때는 공유 유틸리티로 추출하는 걸 권장한다(지금은 시기상조,
YAGNI — 정확히는 "지금 당장 안전하게 할 시간이 없어서 미루는 것"이지
"영원히 필요 없다"는 뜻은 아님).

### API

```
DELETE /api/v1/users/me
```

응답: `204 No Content`

패키지 위치: `/users`는 `/auth`, `/captures`, `/home`, `/storage`,
`/search`와 다른 독립 리소스 경로라 `UserController`/`UserApiDocs`/
`UserService`를 `cmc.recap.user` 패키지에 신규 생성한다.

### 에러 코드

| 상황 | ErrorCode | HTTP |
| --- | --- | --- |
| 이미 탈퇴한 유저가 재요청 | `ALREADY_WITHDRAWN` | 409 |

### Access Token 잔존 리스크

ADR-0015에서 이미 감수하기로 확정한 리스크. 이번 LLD에서 별도 방어
로직(예: 매 요청마다 DB로 상태 확인)을 추가하지 않는다.

## 고려한 대안 (Considered Options)

1. **OrganizeService.deleteCards()를 공유 유틸로 즉시 추출 (기각,
   지금은)** — 일정 촉박, 이미 동작 중인 코드를 건드리는 리스크가
   지금 얻는 이득보다 큼. 세 번째 필요 시점(다중 삭제 API)에 재검토.
2. **탈퇴 시 Access Token 즉시 무효화(매 요청 DB 확인) (기각)** —
   모든 인증 API에 DB 조회가 추가되는 성능 트레이드오프, 리스크
   심각도가 낮아 감수하는 쪽을 택함(ADR-0015).

## 결과 (Consequences)

### 긍정
- 기존에 검증된 패턴(S3 먼저 삭제 → DB 나중, 소유권 검증 등)을
  그대로 재사용해 새로운 리스크가 적음.

### 부정 / 트레이드오프
- S3 배치 삭제 로직이 `OrganizeService`와 `UserService` 두 곳에
  중복 존재하게 됨 — 후속 이슈에서 정리 필요.

## 후속 / 미결정

- [ ] 다중 캡처 삭제 API(이슈 E) 구현 시, S3 배치 삭제 로직을
      공유 유틸리티로 추출할지 재검토(세 번째 발생 시점)
