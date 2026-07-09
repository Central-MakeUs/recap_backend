# LLD-0001: 카카오/Apple 소셜 로그인

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted (개정) |
| 날짜 | 2026-07-07 최초 확정, 2026-07-10 개정(email 제거, Android Apple 콜백 플로우 반영) |
| 관련 | [ADR-0001](../adr/ADR-0001-deviceid-anonymous-identity.md), User 엔티티 |

## 개정 이력

- 2026-07-10: RECAP 서버는 email을 사용하지 않는 것으로 확정. Apple의
  "최초 로그인 시에만 이메일 제공" 관련 로직 전체 제거. Apple이 Android
  네이티브 SDK를 제공하지 않아 Android + Apple 조합만 별도 콜백 플로우가
  필요함을 반영. API 경로에 `/v1` 프리픽스 추가.
- 2026-07-10 (2차): Android Apple 콜백 후 앱으로 돌아가는 딥링크 스킴을
  `com.cmc.recap://oauth/callback`으로 확정. RFC 8252(OAuth 2.0 for Native
  Apps)에 따르면 커스텀 URI 스킴은 다른 앱이 동일 스킴을 등록해 응답을
  가로챌 수 있는 한계가 있어, 실제 토큰을 딥링크에 직접 담지 않기로 결정.
  대신 1회용·단기 유효 교환 코드(`exchangeCode`)만 전달하고, 앱이 별도
  백채널 POST로 실제 토큰을 받는 구조로 변경.

## 맥락 (Context)

ADR-0001에 따라 RECAP MVP는 deviceId 기반 익명 식별로 시작했으나, 클라우드
백업/기기 교체 복원, 구독 결제, 팀 공유 등 로드맵 기능은 영속적 계정 식별을
요구한다. App Store 심사 가이드라인 4.8에 의해 제3자 소셜 로그인을 제공하면
Sign in with Apple을 함께 제공해야 하므로, 카카오와 Apple 로그인을 한 세트로
설계한다.

**RECAP 서버는 email을 어떤 용도로도 사용하지 않는다.** 유저 식별은
`(oauthProvider, oauthId)` 조합만으로 완결되며, 카카오 동의항목에서 이메일을
요청하지 않고 Apple identityToken에서도 email 클레임을 사용하지 않는다.

## 결정 (Decision)

### 인증 흐름 — 두 가지 패턴이 공존한다

**패턴 A. 클라이언트 토큰 포워딩 (iOS Apple, iOS/Android Kakao)**

앱이 SDK로 provider 토큰(또는 identityToken)을 직접 수신하고, 이를 백엔드로
전달한다. 백엔드는 이 값을 provider 서버에 검증한 뒤 자체 JWT를 발급한다.

```
POST /api/v1/auth/oauth/{provider}/login
```

**패턴 B. 서버 콜백 (Android Apple 전용, 예외)**

Apple은 Android 네이티브 SDK를 제공하지 않는다. Android는 웹 기반 OAuth
플로우(Custom Tab/WebView)를 쓸 수밖에 없고, 이 경우 Apple은 인증 결과를
**앱이 아니라 서버의 Return URL로 직접** POST(form_post)한다. 서버는 이를
받아 처리한 뒤, 앱으로 결과를 넘겨주기 위해 딥링크로 리다이렉트한다.

```
(브라우저) -> https://appleid.apple.com/auth/authorize
              ?client_id={Android Services ID}
              &redirect_uri=https://re-cap.duckdns.org/api/v1/auth/apple/android/callback
              &response_type=code
              &response_mode=form_post
              &state={csrf 방지용 랜덤값}
              (scope 없음 - email/name 미요청)

(Apple)    -> POST https://re-cap.duckdns.org/api/v1/auth/apple/android/callback
              (code, state 포함, id_token은 response_type에 따라 포함될 수 있음)

(서버)     -> code/id_token 검증 -> JWT 발급
           -> 1회용/60초 유효 exchangeCode 발급 (인메모리 캐시에 토큰과 매핑해 보관)
           -> 302 리다이렉트:
              com.cmc.recap://oauth/callback?exchangeCode={코드}&state={state}

(앱)       -> 딥링크 수신, state 검증
           -> POST /api/v1/auth/apple/android/exchange { "exchangeCode": "..." }
           -> 응답 바디로 실제 TokenResponse(accessToken, refreshToken) 수신
```

**딥링크에 실제 토큰을 직접 담지 않는 이유**: RFC 8252(OAuth 2.0 for Native
Apps)는 커스텀 URI 스킴을 다른 앱이 동일하게 등록해 응답을 가로챌 수 있는
한계를 명시한다. 딥링크에 살아있는 accessToken/refreshToken을 그대로 실으면
가로채였을 때 즉시 로그인 상태를 탈취당한다. 대신 1회용·단기 유효
exchangeCode만 전달하고, 실제 토큰은 앱이 서버에 백채널 POST로 요청해 받는다
— 가로채이더라도 공격자가 만료 전에 교환 요청까지 먼저 성공시켜야 하는
좁은 창만 남는다. 완전한 PKCE 도입은 Android 클라이언트의 추가 암호화
구현이 필요해 지금 범위에서는 제외하고 후속 과제로 남긴다.

이 예외는 임의로 흐름을 통일하지 않은 이유가 있다. Apple의 플랫폼 제약(Android
네이티브 SDK 부재)이 강제하는 구조이지, 설계상 선택의 문제가 아니다.

### 검증 방식

- 카카오: `RestClient`로 `GET https://kapi.kakao.com/v2/user/me` 호출
  (`Authorization: Bearer {token}`). 응답에서 `id`(회원번호)만 사용한다.
- Apple: `identityToken`(JWT)을 Apple의 JWKS(`https://appleid.apple.com/auth/keys`)로
  서명 검증한다. `sub` 클레임을 `oauthId`로 사용한다.
- **email은 어느 provider에서도 추출/저장하지 않는다.** `User.email`
  필드는 값이 채워지지 않는 상태로 유지된다(계정 구조 확장 여지로만 보존).

### Apple 이중 audience 검증 (필수)

iOS 네이티브와 Android 웹 플로우는 identityToken의 `aud`(대상) 클레임 값이
서로 다르다. 서버는 둘 다 유효하다고 판단해야 한다.

```java
private static final Set<String> VALID_APPLE_AUDIENCES = Set.of(
        "com.cmc.recap",          // iOS Bundle ID (App ID)
        "com.cmc.recap.service"   // Android Services ID
);
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
| exchangeCode 유효시간 | 60초, 1회용 | Android Apple 콜백 전용, 인메모리 캐시 보관 (단일 인스턴스 전제, 후속 확장 시 Redis 검토) |

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
| POST | /api/v1/auth/oauth/{provider}/login | 패턴 A: provider(kakao\|apple) 토큰으로 로그인/가입 |
| POST | /api/v1/auth/apple/android/callback | 패턴 B: Apple이 직접 호출하는 콜백 전용(Android만) |
| POST | /api/v1/auth/apple/android/exchange | 패턴 B: exchangeCode를 실제 TokenResponse로 교환 (Android만) |
| POST | /api/v1/auth/refresh | Refresh Token으로 Access Token 재발급(로테이션 포함) |
| POST | /api/v1/auth/logout | Refresh Token 무효화 |

### DTO

```java
// 패턴 A 요청 (iOS Apple, iOS/Android Kakao)
record OAuthLoginRequest(
        String deviceId,
        String providerToken,
        Platform platform
) {}

// 패턴 B는 Apple이 form_post로 직접 호출하므로 JSON DTO가 아니라
// application/x-www-form-urlencoded 폼 파라미터(code, state, [id_token])로 수신

// 패턴 B 2단계: 앱이 딥링크로 받은 exchangeCode를 실제 토큰으로 교환
record ExchangeCodeRequest(
        String exchangeCode
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

### 도메인 검증 (Android Apple 전용, 인프라 요구사항)

Apple은 Return URL을 등록하려면 도메인 소유권 검증을 요구한다. 다음 파일을
https://re-cap.duckdns.org/.well-known/apple-developer-domain-association.txt
경로로 정적 서빙해야 한다 (Nginx 설정 참고).

## 고려한 대안 (Considered Options)

1. 모든 provider/플랫폼을 단일 엔드포인트로 통일 (기각) - Apple의
   Android SDK 부재라는 플랫폼 제약상 불가능. Android Apple만은 서버가
   콜백을 직접 받아야 하는 구조를 피할 수 없다.
2. Android도 웹뷰 안에서 딥링크 없이 완결 (기각) - Apple 콜백이 서버로
   오는 이상, 앱으로 제어권을 넘기려면 딥링크(또는 최소한 커스텀 스킴
   리다이렉트)가 필수적이다.
3. email 계속 사용 + 최초 1회 저장 로직 유지 (기각) - 서버가 email을
   쓰지 않기로 확정되어 관련 로직 전체가 불필요한 복잡도였다. 제거로
   Apple/Kakao 양쪽 로직이 모두 단순해짐.
4. 딥링크에 실제 accessToken/refreshToken을 직접 담기 (기각) - RFC 8252가
   명시한 커스텀 스킴 가로채기 위험 때문에, 살아있는 토큰이 그대로 유출될
   수 있음. 1회용/60초 exchangeCode + 백채널 POST 교환 방식(채택)으로
   위험을 좁은 시간창으로 축소.

## 결과 (Consequences)

### 긍정
- email 제거로 Apple "최초 1회 이메일" 처리 로직, 카카오 이메일 동의항목
  심사가 모두 불필요해짐 - 인증 로직이 단순해지고 카카오 쪽은 활성화만
  하면 됨(추가 심사 없음).
- 카카오 계정에 이메일이 없는 유저도 로그인 실패 없이 정상 처리됨.

### 부정 / 트레이드오프
- Android Apple 로그인만 별도 흐름(서버 콜백 + exchangeCode 교환)이라
  구현/테스트 경로가 하나 더 늘어난다.
- exchangeCode를 인메모리 캐시로 관리하므로, 향후 서버를 여러 인스턴스로
  수평 확장하면 캐시 공유 문제가 생겨 Redis 등으로 전환이 필요해진다
  (현재 단일 EC2 인스턴스 전제에서는 문제없음).

## 후속 / 미결정

- [x] ~~딥링크 스킴 확정~~ → `com.cmc.recap://oauth/callback`으로 확정
  (2026-07-10). Android 앱은 이 스킴을 인텐트 필터로 등록해야 한다.
- [ ] Apple 개발자 계정 이전(조직 변경) 시 sub 값이 바뀔 수 있다는
  커뮤니티 보고가 있음. 현재 계획 없어 지금 대응하지 않음.