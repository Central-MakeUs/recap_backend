# LLD-0001: 카카오/Apple 소셜 로그인

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-07 (Draft 작성 2026-07-07, 확정 2026-07-07) |
| 관련 | [ADR-0001](../adr/ADR-0001-deviceid-anonymous-identity.md), User 엔티티 |

## 맥락 (Context)

ADR-0001에 따라 RECAP MVP는 deviceId 기반 익명 식별로 시작했으나, 클라우드
백업/기기 교체 복원, 구독 결제, 팀 공유 등 로드맵 기능은 영속적 계정 식별을
요구한다. App Store 심사 가이드라인 4.8에 의해 제3자 소셜 로그인을 제공하면
Sign in with Apple을 함께 제공해야 하므로, 카카오와 Apple 로그인을 한 세트로
설계한다.

## 결정 (Decision)

### 인증 흐름

- 앱(iOS/Android)이 카카오/Apple SDK로 provider 토큰(또는 ID token)을
  직접 수신하고, 이를 백엔드로 전달한다. 백엔드는 이 값을 provider 서버에
  검증한 뒤 자체 JWT(Access/Refresh)를 발급한다 (ADR-0001 확정).
- 검증 방식: Spring Security `OAuth2Client`(서버 주도 리다이렉트 플로우)를
  사용하지 않는다. 이 프로젝트의 흐름은 "클라이언트가 이미 받은 토큰을
  서버가 검증"하는 패턴이라 성격이 다르기 때문이다.
    - 카카오: `RestClient`로 `GET https://kapi.kakao.com/v2/user/me`
      호출(`Authorization: Bearer {token}`)해 사용자 식별 정보를 얻는다.
    - Apple: `identityToken`(JWT)을 Apple의 JWKS
      (`https://appleid.apple.com/auth/keys`)로 서명 검증한다.

### 토큰 체계

| 항목 | 값 | 근거 |
| --- | --- | --- |
| Access Token 만료 | 30분 | 업계 권장 범위(15~60분) 내 중간값. 모바일 백그라운드 전환 빈도를 고려 |
| Refresh Token 만료 | 14일 | 업계 권장 범위(7~14일). 리텐션 지표로 추후 조정 가능 |
| Refresh Token 저장 | SHA-256 해시 저장 (원문 미저장) | OWASP/업계 권고. DB 유출 시 토큰 전체 탈취 방지 |
| 로테이션 정책 | 재발급 시 기존 Refresh Token 즉시 폐기(single-use) | 재전송(replay) 공격 방지 |
| 서명 키 | 대칭키(HS256), 환경변수(`JWT_SECRET`)로 관리 | 현재 인프라(단일 EC2, KMS 미도입) 규모에서 RS256+키 로테이션은 과설계로 판단 |
| API 버전 프리픽스 | 없음 (`/api/auth/...`) | 현재 버전 분기 요구 없음. 필요 시점에 도입 |

### 계정 식별 및 병합

- 로그인 요청에는 `deviceId`를 **항상 필수**로 받는다(최초 가입/재로그인
  공통).
- 조회 순서: `oauthProvider + oauthId`로 기존 유저를 먼저 조회한다.
    - 있으면 해당 유저로 로그인 처리(요청의 deviceId는 무시하거나 최근
      접속 기기 기록용으로만 갱신 — 계정의 진짜 식별자는 oauthId).
    - 없으면 `deviceId`로 기존 익명 유저를 조회해 `linkOauth`로 병합한다.
    - 그마저 없으면(deviceId도 처음 보는 값) 신규 `User`를 생성한다.
- 병합 시나리오(기기 A 익명 사용 → 기기 B 로그인 → 기기 A 재로그인): 로그인
  시점부터는 deviceId가 아니라 `oauthId`가 진짜 식별자이므로, 기기가
  달라도 동일 계정으로 정상 로그인 처리한다. 기존 deviceId 기반 데이터는
  그대로 유지되며 별도 충돌 처리가 필요 없다.
- 완전 신규 유저(oauthId, deviceId 둘 다 미존재) 생성 시 필요한 최소
  정보는 기존 `User` 엔티티 설계로 이미 해결되어 있다: `platform`은
  엔티티 상 `nullable = false`이므로 로그인 요청에도 필수로 받는다.

### Apple 이메일 처리

Apple은 이메일 필드를 **최초 인가 시점에만** 내려주고 이후 로그인부터는
제공하지 않는다(Apple 개발자 문서/포럼에서 확인된 사실, 선택 사항이 아님).
따라서 최초 로그인 시점에 수신한 이메일을 `User.email`에 저장해두고,
이후 로그인에서는 재요청하지 않는다.

### API 엔드포인트

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/auth/oauth/{provider}/login` | provider(`kakao`\|`apple`) 토큰으로 로그인/가입 |
| POST | `/api/auth/refresh` | Refresh Token으로 Access Token 재발급(로테이션 포함) |
| POST | `/api/auth/logout` | Refresh Token 무효화 (이번 범위 포함) |

단일 엔드포인트(`{provider}` path variable) 구조로 확정. provider별 요청
필드 차이(카카오 access token vs Apple identity token)는 서비스 레이어의
전략 객체(`OAuthProvider` 인터페이스 구현체)로 분기한다.

### DTO

```java
// 요청
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

// 응답
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
    private String tokenHash; // SHA-256(원문), 원문은 저장하지 않음

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

### 로그인 처리 전략 (OCP 고려)

provider가 늘어날 가능성(구글 등)을 고려해, provider별 검증 로직을
인터페이스로 분리한다. 새 provider 추가 시 기존 코드를 건드리지 않고
구현체만 추가하면 되도록 한다.

```java
public interface OAuthProvider {
    OAuthUserInfo verify(String providerToken);
    String providerName(); // "kakao", "apple"
}

public record OAuthUserInfo(
        String oauthId,
        String email // 없을 수 있음(nullable)
) {}
```

## 고려한 대안 (Considered Options)

1. **provider별 엔드포인트 분리** (`/kakao/login`, `/apple/login`) —
   컨트롤러 레벨에서 분기가 명확하나 provider 추가 시 컨트롤러가 늘어남.
2. **단일 엔드포인트** (`/oauth/{provider}/login`) (채택) — 경로가
   간결하고, provider별 차이는 `OAuthProvider` 전략 인터페이스로 흡수해
   컨트롤러가 아닌 서비스 레이어에서 처리한다.

## 결과 (Consequences)

### 긍정
- ADR-0001의 병합 전략을 그대로 재사용, `User` 엔티티 변경 없이 인증
  계층만 추가.
- Refresh Token 해시 저장 + 로테이션으로 토큰 탈취 시 피해 범위를 최소화.
- provider 확장이 `OAuthProvider` 구현체 추가만으로 가능(OCP).

### 부정 / 트레이드오프
- 대칭키(HS256) 방식은 추후 다른 서비스와 토큰을 공유해야 하는 상황이
  오면 RS256으로 전환이 필요.
- Access Token 30분/Refresh 14일은 업계 권장 범위 기반 초깃값이며, 실사용
  리텐션 데이터로 검증되지 않았다(추후 조정 가능성 있음).

## 후속 / 미결정

- [ ] Apple 개발자 계정 이전(조직 변경) 시 사용자 식별자가 바뀔 수 있다는
  커뮤니티 보고가 있음. 현재 팀 이전 계획이 없어 지금 대응하지 않으나,
  추후 조직 구조 변경 시 재검토 필요(근거가 Apple 공식 문서가 아닌
  개발자 커뮤니티 보고라 일반화에 주의).