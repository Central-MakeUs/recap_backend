# LLD-0001: 카카오/Apple 소셜 로그인

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted (개정) |
| 날짜 | 2026-07-07 최초 확정, 2026-07-10 개정(email 제거, Apple 로그인 iOS 전용으로 확정) |
| 관련 | [ADR-0001](../adr/ADR-0001-deviceid-anonymous-identity.md), [ADR-0008](../adr/ADR-0008-exchange-code-deeplink-pattern.md)(현재 미적용, 하단 참고), User 엔티티 |

## 개정 이력

- 2026-07-10: RECAP 서버는 email을 사용하지 않는 것으로 확정. Apple의
  "최초 로그인 시에만 이메일 제공" 관련 로직 전체 제거. API 경로에 `/v1`
  프리픽스 추가.
- 2026-07-10 (2차, 이후 3차 결정으로 대부분 롤백됨): Android + Apple 웹
  기반 콜백 플로우를 설계하고, 딥링크 가로채기 위험 대응으로
  exchangeCode 패턴([ADR-0008](../adr/ADR-0008-exchange-code-deeplink-pattern.md))을
  도입했었음.
- 2026-07-10 (3차): **Apple 로그인은 iOS에서만 제공하는 것으로 확정.**
  Android는 카카오 로그인만 제공한다. 이에 따라 Android + Apple 웹 콜백
  플로우, exchangeCode 패턴, Apple Services ID 관련 설정이 전부
  불필요해져 제거함. 상세 배경은 [ADR-0009](../adr/ADR-0009-apple-login-ios-only.md) 참고.

## 맥락 (Context)

ADR-0001에 따라 RECAP MVP는 deviceId 기반 익명 식별로 시작했으나, 클라우드
백업/기기 교체 복원, 구독 결제, 팀 공유 등 로드맵 기능은 영속적 계정 식별을
요구한다. App Store 심사 가이드라인 4.8에 의해 제3자 소셜 로그인을 제공하면
Sign in with Apple을 함께 제공해야 하므로, 카카오와 Apple 로그인을 함께
설계한다.

**Apple 로그인은 iOS에서만 제공한다.** Apple이 Android 네이티브 SDK를
제공하지 않아 Android에서 지원하려면 웹 기반 플로우(서버 콜백, 딥링크,
Services ID 등)가 추가로 필요한데, ADR-0009에 따라 이 복잡도를 감수하지
않고 Android는 카카오 로그인만 제공하기로 결정했다.

**RECAP 서버는 email을 어떤 용도로도 사용하지 않는다.** 유저 식별은
`(oauthProvider, oauthId)` 조합만으로 완결되며, 카카오 동의항목에서 이메일을
요청하지 않고 Apple identityToken에서도 email 클레임을 사용하지 않는다.

## 결정 (Decision)

### 인증 흐름 — 단일 패턴

모든 provider·플랫폼 조합(iOS Apple, iOS/Android Kakao)이 동일한 패턴을
따른다. 앱이 SDK로 provider 토큰(또는 identityToken)을 직접 수신하고, 이를
백엔드로 전달한다. 백엔드는 이 값을 provider 서버에 검증한 뒤 자체 JWT를
발급한다.

```
POST /api/v1/auth/oauth/{provider}/login
```

Apple이 iOS 전용으로 확정되면서, 서버가 provider로부터 직접 콜백을 받는
예외 흐름 자체가 필요 없어졌다. 모든 로그인이 "앱이 토큰을 받아 서버로
전달"하는 하나의 흐름으로 통일된다.

### 검증 방식

- 카카오: `RestClient`로 `GET https://kapi.kakao.com/v2/user/me` 호출
  (`Authorization: Bearer {token}`). 응답에서 `id`(회원번호)만 사용한다.
- Apple: `identityToken`(JWT)을 Apple의 JWKS(`https://appleid.apple.com/auth/keys`)로
  서명 검증한다. `sub` 클레임을 `oauthId`로 사용한다. `aud` 클레임은
  iOS Bundle ID(`com.cmc.recap`) 하나와만 비교한다(Android 대응이 없으므로
  이중 검증 불필요).
- **email은 어느 provider에서도 추출/저장하지 않는다.** `User.email`
  필드는 값이 채워지지 않는 상태로 유지된다(계정 구조 확장 여지로만 보존).

```java
private static final String VALID_APPLE_AUDIENCE = "com.cmc.recap"; // iOS Bundle ID
```

### 토큰 체계

| 항목 | 값 | 근거 |
| --- | --- | --- |
| Access Token 만료 | 30분 | 업계 권장 범위(15~60분) 내 중간값 |
| Refresh Token 만료 | 14일 | 업계 권장 범위(7~14일) |
| Refresh Token 저장 | SHA-256 해시 저장 (원문 미저장) | DB 유출 시 토큰 전체 탈취 방지 |
| 로테이션 정책 | 재발급 시 기존 Refresh Token 즉시 폐기 | 재전송 공격 방지 |
| 서명 키 | 대칭키(HS256), 환경변수(JWT_SECRET) | 현재 인프라 규모에서 RS256+로테이션은 과설계 |
| API 버전 프리픽스 | /api/v1 | 확정 |

### 계정 식별 및 병합

- 로그인 요청에는 deviceId를 항상 필수로 받는다.
- 조회 순서: oauthProvider + oauthId로 기존 유저 먼저 조회 -> 없으면
  deviceId로 익명 유저 조회 후 linkOauth로 병합 -> 그마저 없으면 신규
  User 생성.
- 병합 시나리오: 로그인 이후에는 oauthId가 진짜 식별자이므로, 기기가
  달라도 동일 계정으로 정상 로그인 처리한다.

### API 엔드포인트

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | /api/v1/auth/oauth/{provider}/login | provider(kakao\|apple) 토큰으로 로그인/가입 |
| POST | /api/v1/auth/refresh | Refresh Token으로 Access Token 재발급(로테이션 포함) |
| POST | /api/v1/auth/logout | Refresh Token 무효화 |

### DTO

```java
record OAuthLoginRequest(
        String deviceId,
        String providerToken,
        Platform platform
) {}

record TokenRefreshRequest(
        String refreshToken
) {}

record LogoutRequest(
        String refreshToken
) {}

record TokenResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt
) {}
```

### RefreshToken 엔티티

```java
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    public static RefreshToken issue(User user, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.revoked = false;
        return token;
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }
}
```

## 고려한 대안 (Considered Options)

1. Apple 로그인을 iOS/Android 둘 다 지원 (기각, 상세는 ADR-0009) - Android
   지원을 위해 웹 기반 콜백 플로우, 딥링크 보안 대응, Services ID 관리
   등 상당한 복잡도가 추가되는데, 그 복잡도를 감수할 필요가 없다고 판단.
2. Apple 로그인을 iOS 전용으로 제한 (채택) - 모든 로그인이 "앱이 토큰을
   받아 서버로 전달"하는 단일 패턴으로 통일되어 구현/테스트가 단순해짐.
3. email 계속 사용 + 최초 1회 저장 로직 유지 (기각) - 서버가 email을
   쓰지 않기로 확정되어 관련 로직 전체가 불필요한 복잡도였다.

## 결과 (Consequences)

### 긍정
- 모든 로그인이 단일 패턴(클라이언트 토큰 포워딩)으로 통일됨 - 구현체가
  `OAuthProvider` 인터페이스 하나로 깔끔하게 정리됨.
- 서버 콜백, 딥링크, exchangeCode 캐시, 이중 audience 검증이 전부
  불필요해져 구현 범위가 크게 줄어듦.
- email 제거로 Apple "최초 1회 이메일" 처리 로직, 카카오 이메일 동의항목
  심사가 모두 불필요해짐.

### 부정 / 트레이드오프
- Android 사용자는 Apple 계정으로 로그인할 수 없다 - 카카오 계정이 없는
  Android 사용자는 가입 자체가 불가능해질 수 있다(제품 관점에서 인지 필요).

## 후속 / 미결정

- [ ] Android에서 Apple 계정만 있고 카카오 계정이 없는 사용자의 유입
  손실 규모를 추후 지표로 관찰할지 여부 (제품 논의 필요)
- [ ] 이미 생성한 Apple Services ID(`com.cmc.recap.service`)를 삭제할지,
  추후 재도입 가능성을 열어두고 그냥 둘지 (급하지 않음)