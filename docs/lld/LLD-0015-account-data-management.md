# LLD-0015: 계정 정보 · 데이터 관리 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-28 |
| 관련 | [ADR-0015](../adr/ADR-0015-user-withdrawal-hybrid-deletion.md), [LLD-0009](LLD-0009-user-withdrawal.md), [LLD-0012](LLD-0012-image-expiration-batch.md), [LLD-0013](LLD-0013-bulk-capture-delete.md) |

## 맥락 (Context)

08-03(계정 관리), 08-04(데이터 관리) 화면에 필요한 API를 구현한다.
"전체 데이터 삭제"는 회원탈퇴(`UserService.withdraw()`, LLD-0009)가
이미 수행하는 로직의 부분집합(캡처+이미지 삭제)이다 — 계정 삭제·
로그인 세션 폐기는 포함하지 않는다.

## 결정 (Decision)

### 경로 통일 — `/users/me` 하위

```
GET    /api/v1/users/me                로그인 플랫폼 + 가입일
GET    /api/v1/users/me/data-summary   정리된 캡처 개수
DELETE /api/v1/users/me/data           캡처+원본 이미지 삭제 (계정 유지)
```

`DELETE /api/v1/users/me`(회원탈퇴, 기존)와 경로 계층을 공유한다.

### `UserService.withdraw()`에서 캡처 삭제 로직 추출

**기존 `withdraw()` 내부에 있던 "InfoCard+S3 이미지 전체 삭제"
로직을 `deleteAllCaptures(User user)`로 추출**하고, `withdraw()`와
신규 `deleteAccountData()`가 공유한다.

```java
// UserService.java (기존 withdraw() 리팩터링)
@Transactional
public void withdraw(Long userId) {
    User user = getUser(userId);
    deleteAllCaptures(user);
    refreshTokenRepository.deleteByUser(user);
    user.withdraw();
}

@Transactional
public void deleteAccountData(Long userId) {
    User user = getUser(userId);
    deleteAllCaptures(user);
    // RefreshToken 폐기 없음, user.withdraw() 호출 없음 — 계정/세션 유지
}

private void deleteAllCaptures(User user) {
    List<InfoCard> cards = infoCardRepository.findByUser(user);
    List<String> imageKeys = cards.stream()
            .map(InfoCard::getOriginalImageKey)
            .filter(Objects::nonNull) // LLD-0012: 만료된 캡처는 키가 null
            .toList();
    deleteS3ObjectsInChunks(imageKeys); // 1000개 단위 청크(LLD-0013과 동일 원칙)
    infoCardRepository.deleteAll(cards);
}

private User getUser(Long userId) {
    return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
}
```

**주의**: `deleteAccountData()`는 `RefreshToken` 폐기와 `user.withdraw()`
(익명화)를 절대 호출하지 않는다 — 이 둘을 실수로 재사용하면 사용자가
데이터만 지웠는데 로그아웃되거나 계정이 익명화되는 버그가 된다.

기존 `withdraw()`의 S3 배치 삭제 부분이 1000개 청크 분할을 안 하고
있었다면, 이번 추출 시점에 LLD-0013과 동일한 청크 분할을 추가한다
(구현 시 기존 코드 확인 필요).

### 응답 DTO

```java
public record AccountInfoResponse(String platform, Instant createdAt) {
    public static AccountInfoResponse from(User user) {
        return new AccountInfoResponse(
                user.getOauthProvider().name().toLowerCase(), user.getCreatedAt());
    }
}

public record DataSummaryResponse(long capturedCount) {
    public static DataSummaryResponse of(long capturedCount) {
        return new DataSummaryResponse(capturedCount);
    }
}
```

`platform`은 소문자 `"apple"`/`"kakao"`로 응답한다(화면 표기 및
이슈 원문 형식과 일치).

### 신규 Repository 메서드

```java
long countByUser(User user); // InfoCardRepository, data-summary용
```

`findByUser(User user)`는 이미 LLD-0009(회원탈퇴)에서 추가되어
재사용 가능.

## 고려한 대안 (Considered Options)

1. **`/account` 경로 유지 (기각)** — `DELETE /users/me`(회원탈퇴)와
   경로 루트가 갈려 API 일관성이 떨어짐.
2. **`withdraw()` 로직을 복사해서 새로 작성 (기각)** — 네 번째
   중복이 되는 데다, 이번엔 추출 비용이 낮아(다른 서비스 클래스까지
   안 건드림) 굳이 중복할 이유가 없음.
3. **캡처 삭제 로직 추출 + 공유 (채택)**.

## 결과 (Consequences)

### 긍정
- `withdraw()`와 `deleteAccountData()`가 캡처 삭제 로직을 공유해
  향후 이 로직 변경 시 한 곳만 고치면 됨.
- 기존 회원탈퇴 API와 경로 체계가 일관됨.

### 부정 / 트레이드오프
- `UserService`가 하는 일이 늘어나 클래스가 다소 커짐 — 지금 규모
  에선 분리할 정도는 아니라고 판단.

## 후속 / 미결정

- 없음.
