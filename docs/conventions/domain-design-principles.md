# 도메인 설계 원칙 (Domain Design Principles)

> DB 스키마(ERD)가 아니라 **엔티티/도메인 객체를 어떻게 작성할지에 대한
> 코드 규칙**을 담는다. ERD-0001이 생기면 스키마 문서는 `docs/erd/`에
> 별도로 작성하고, 이 문서는 계속 코드 작성 규칙으로 유지한다.

## 핵심 원칙 — Always-Valid Domain Model (자가 검증 도메인)

**어떤 경로로도 유효하지 않은 상태의 엔티티가 존재할 수 없어야 한다.**
검증을 서비스 레이어의 책임으로 미루지 않고, 엔티티 스스로가 자신의
불변식(invariant)을 지킨다.

## 1. 생성 규칙

- 생성자는 `private`으로 막는다. `@NoArgsConstructor(access = AccessLevel.PROTECTED)`는
  JPA 스펙상 필수이므로 유지하되, 의미 있는 생성자는 `private`으로 감싼다.
- 인스턴스 생성은 **정적 팩토리 메서드**로만 한다. 생성 목적이 다르면
  이름이 다른 팩토리를 여러 개 둔다 (예: `createByDevice`, `createAdmin`
  처럼 하나의 범용 생성자에 플래그를 넘기지 않는다).
- 생성 시점에 검증한다. 검증 실패는 `IllegalArgumentException` 같은
  원시 예외가 아니라 **`BusinessException(ErrorCode)`** 를 던진다
  (`cmc.recap.global.exception.model.BusinessException` 이미 존재함).

```java
class User {

  public static User createByDevice(String deviceId, Platform platform) {
    validateDeviceId(deviceId);
    User user = new User();
    user.deviceId = deviceId;
    user.platform = platform;
    return user;
  }

  private static void validateDeviceId(String deviceId) {
    if (deviceId == null || deviceId.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "deviceId는 필수입니다.");
    }
  }
}
```

## 2. 상태 변경 규칙

- **setter를 두지 않는다.** 상태 변경은 의도가 드러나는 이름의 메서드로만
  한다 (`updateInfo()`, `linkOauth()`, `revoke()` 등).
- 변경 메서드도 생성자와 동일하게 검증한다. "생성할 때만 유효하고 변경
  후엔 깨질 수 있는" 엔티티는 이 원칙 위반이다.
- 이미 특정 상태인데 다시 그 상태로 전이를 시도하면(예: 이미 연결된
  OAuth를 재연결, 이미 폐기된 토큰을 재폐기) 조용히 무시하지 말고
  `BusinessException`으로 명시적으로 막는다.

```java
class User {

    public void linkOauth(String email, String oauthProvider, String oauthId) {
        if (this.oauthId != null) {
            throw new BusinessException(ErrorCode.ALREADY_LINKED_OAUTH);
        }
        this.email = email;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
    }
}
```

## 3. 조회 메서드 규칙 — Tell, Don't Ask

외부에서 필드를 꺼내 조건문으로 판단하게 하지 않는다. 판단 로직 자체를
엔티티가 `boolean` 메서드로 제공한다.

```java
class Example {

    // 지양
    void withoutTellDontAsk() {
        if (refreshToken.getExpiresAt().isAfter(Instant.now()) && !refreshToken.isRevoked()) {
            // 로그인 유지 처리
        }
    }

    // 지향 — 이미 RefreshToken.isUsable()이 이 형태로 되어 있음, 이 패턴을 표준으로 삼는다
    void withTellDontAsk() {
        if (refreshToken.isUsable()) {
            // 로그인 유지 처리
        }
    }
}
```

## 4. 예외 규칙

- 도메인 규칙 위반은 전부 `BusinessException(ErrorCode)`로 던진다.
  `IllegalStateException`, `IllegalArgumentException` 같은 원시 예외를
  도메인 로직에서 직접 던지지 않는다 (원시 예외는 `GlobalExceptionHandler`의
  범용 핸들러가 잡아 `INVALID_INPUT`/`INTERNAL_ERROR`로 뭉뚱그리므로,
  실제 의미(409 Conflict 등)를 잃는다).
- 새 도메인 규칙을 추가하면서 마땅한 `ErrorCode`가 없다면, 기존 것에
  억지로 끼워맞추지 말고 `ErrorCode`를 추가한다.

## 5. DTO 생성 규칙

- 서비스/파사드 레이어에서 `new XxxResponse(...)`로 직접 생성하지 않는다.
  DTO 자신에게 정적 팩토리를 두고 그걸 통해서만 만든다.
- 명명 규칙: **`from(Entity)`는 도메인 엔티티를 변환할 때**,
  **`of(값1, 값2...)`는 개별 값을 조합할 때** 사용한다. Request DTO처럼
  프레임워크(Jackson)가 자동으로 바인딩하는 경우는 팩토리가 필요 없다.

```java
// from() — 엔티티 변환
record UserSummaryResponse(Long id, Platform platform) {
    static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getPlatform());
    }
}

// of() — 값 조합
record TokenResponse(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {
    static TokenResponse of(String accessToken, String refreshToken, Instant expiresAt) {
        return new TokenResponse(accessToken, refreshToken, expiresAt);
    }
}
```

## 6. 레이어링 규칙

- Controller에서 Repository를 직접 import하지 않는다. Controller →
  Service(또는 Facade) → Repository 순서를 지킨다.
- 하나의 유스케이스가 서비스 2개 이상을 조합해야 하면(예: 로그인이
  `UserService` + `RefreshTokenService`를 조합), 파사드를 둘지 하나의
  서비스가 오케스트레이션할지 판단한다. 지금 규모(협업 인원, 도메인 수)에서는
  무리하게 파사드를 도입하지 않고, 조합이 3개 이상으로 늘어나는 시점에
  파사드 도입을 재검토한다 (YAGNI — 지금 단계는 서비스 하나가 오케스트레이션해도 무방).

## 7. 메서드 배치 순서

클래스 위에서 아래로 읽었을 때 "어떻게 쓰는지"가 먼저 나오고
"어떻게 구현됐는지"가 나중에 나오도록, **가시성 기준으로 그룹핑**한다.

```
1. static final 상수
2. 인스턴스 필드
3. 생성자 (private/protected)
4. public static 팩토리 메서드
5. public 인스턴스 메서드 (도메인 행위: updateInfo(), revoke() 등)
6. public 조회 메서드 (isX() 등 Tell-Don't-Ask 메서드)
7. private/protected 헬퍼 메서드 — 항상 맨 아래에 모아둔다
```

private 메서드가 여기저기 흩어져 있거나, public 메서드 사이에 private
메서드가 끼어 있으면 위반이다. 예시(1번 규칙 코드블록의 `User` 참고)와
`Member.java`(친구 프로젝트 참고 코드)가 이 순서를 따르고 있다.

## 8. 매직 리터럴과 환경변수 구분 규칙

문자열/URL 리터럴이 코드에 하드코딩되어 있으면 무조건 환경변수로 빼는
것이 아니라, 아래 기준으로 분류한다.

```
환경변수(.env, application.yml)로 뺀다
  - 배포 환경(local/prod)마다 값이 달라질 수 있는 것
  - 비밀값(시크릿 키, 자격증명)
  예: JWT_SECRET, APPLE_BUNDLE_ID(추후 dev/prod 앱 분리 시 달라질 수 있음)

Java 상수(private static final)로 남긴다
  - 제3자 서비스 자체의 고정된 값 (환경과 무관하게 절대 안 바뀜)
  - 이걸 환경변수로 빼면 매 환경마다 동일한 값을 실수 없이 입력해야
    하는 부담만 생기고 얻는 유연성이 없다
  예: Apple JWKS URL, Apple issuer, 카카오 사용자 정보 API URL

공용 상수로 통합한다 (환경변수도, 인라인 상수도 아님)
  - 여러 클래스에서 반복 등장하는 내부 식별자 문자열
  - 한 곳에서만 값을 정의하고 나머지는 그 상수를 참조한다
  예: "kakao"/"apple" provider 식별자
```

테스트에서 외부 서비스를 목(mock)으로 대체해야 하면, 환경변수화가 아니라
**생성자 오버로드로 의존성을 주입받는 시임(seam)을 만든다**
(`AppleOAuthProvider(JWKSource<SecurityContext> jwkSource)`처럼). 이미
테스트 가능성이 확보되어 있다면 URL을 환경변수로 뺄 이유가 하나 줄어든다.

## 9. 생성자 오버로드 규칙 (Spring 빈)

- 운영용 생성자와 테스트용 package-private 생성자(시임)를 함께 둔
  Spring 빈(`@Component` 등)은 **운영용 생성자에 반드시 `@Autowired`를
  명시**한다. 생성자가 2개 이상인데 어느 것도 `@Autowired`가 없으면
  Spring이 기본 생성자를 찾다가 `BeanInstantiationException`으로
  기동 자체가 실패한다.
- `AppleOAuthProvider`, `GeminiImageAnalysisProvider`에서 같은 실수가
  반복됐다 — 테스트용 생성자를 추가할 때마다 놓치기 쉬운 지점이므로
  체크리스트에 포함한다.

```java
@Component
public class ExampleProvider {

    @Autowired
    public ExampleProvider(@Value("${example.key}") String key) {
        this(RestClient.builder(), key);
    }

    ExampleProvider(RestClient.Builder restClientBuilder, String key) {
        // 테스트에서 RestClient.Builder를 목으로 주입하기 위한 시임
    }
}
```

## 10. 문자열 자르기(truncate) 규칙

- String 길이를 자르는 로직은 **코드 포인트**(`codePointCount`,
  `offsetByCodePoints`) 단위로 처리한다. `substring(0, N)`처럼 UTF-16
  code unit 기준으로 자르면 서로게이트 쌍(이모지 등 BMP 밖 문자)을
  반으로 끊어 깨진 문자열을 만들 수 있다.
- MySQL `VARCHAR(N)`의 `N`은 charset과 무관하게 항상 "문자(코드 포인트)
  수" 기준이므로, 코드 포인트 단위로 자르는 것이 DB 컬럼 제약과도
  정확히 맞는다.

```java
// 지양 — 서로게이트 쌍을 반으로 끊을 수 있음
String truncated = value.substring(0, maxLength);

// 지향 — 코드 포인트 경계에서만 자른다
if (value.codePointCount(0, value.length()) > maxLength) {
    int cutOffset = value.offsetByCodePoints(0, maxLength - 1);
    String truncated = value.substring(0, cutOffset) + "…";
}
```

## 체크리스트 (구현 시 자가 점검)

- [ ] 이 엔티티는 `new`로 직접 생성 가능한가? → 가능하면 위반
- [ ] 생성/변경 메서드에 검증 없이 필드를 그대로 대입하는 곳이 있는가?
- [ ] 원시 예외(`IllegalStateException` 등)를 도메인 로직에서 던지고 있는가?
- [ ] 서비스 레이어에 `new XxxResponse(` 형태가 남아있는가?
- [ ] Controller가 Repository를 직접 참조하는가?
- [ ] private 메서드가 public 메서드들 사이에 끼어 있지 않고 맨 아래에
  모여 있는가?
- [ ] 환경과 무관한 제3자 서비스 고정값을 환경변수로 빼지 않았는가
  (반대로 뺐는가)?
- [ ] 여러 클래스에 반복되는 문자열 리터럴이 하나의 공용 상수를
  참조하는가?
- [ ] 생성자가 2개 이상인 Spring 빈에서 운영용 생성자에 `@Autowired`가
  빠지지 않았는가?
- [ ] 문자열을 자르는 로직이 `substring(0, N)`(UTF-16 기준)이 아니라
  코드 포인트 기준(`offsetByCodePoints`)으로 되어 있는가?

## 자동 검증

위 규칙 중 기계적으로 검사 가능한 항목은 `scripts/harness/validate-java-rules.sh`로
확인할 수 있다. 이 스크립트는 `.agents/skills` 문서들과 별개다 — 저것들은
"AI가 어떤 절차를 밟는가"를 다루고, 이 스크립트는 "작성된 코드가 규칙을
지켰는가"를 다룬다. 성격이 다른 도구라 하네스의 "문서 기반" 원칙과 상충하지
않는다. 메서드 배치 순서(7번 규칙)는 완벽한 파서가 아니라 휴리스틱
검사이므로("private 메서드가 한 번이라도 나오면 그 뒤엔 public이 없어야
한다") 애매한 케이스는 리뷰어가 직접 판단한다.

```bash
bash scripts/harness/validate-java-rules.sh src/main/java/cmc/recap/auth/domain/RefreshToken.java
```

## 지금 RECAP에서 발견된 위반 사항 (로그인 구현 시 함께 수정)

- `User.createByDevice()` — deviceId 검증 없음. 수정 필요.
- `User.linkOauth()` — 원시 `IllegalStateException` 사용 중. `BusinessException(ErrorCode.ALREADY_LINKED_OAUTH)`로 교체 필요 (`ALREADY_LINKED_OAUTH` ErrorCode는 이전에 이미 제안됨).