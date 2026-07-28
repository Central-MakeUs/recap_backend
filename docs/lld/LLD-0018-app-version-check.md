# LLD-0018: 앱 버전 체크 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-29 |
| 관련 | 이슈 #37 |

## 맥락 (Context)

App Store 심사 제출을 앞두고, v1.0에 반드시 심어둬야 하는 최소
회로로 설계했던 클라이언트 버전 체크 API를 실제로 구현한다. v1.0
시점엔 강제할 구버전이 없어 항상 `forceUpdate: false`를 반환하지만,
이후 버전에서 실제로 강제 업데이트가 필요해질 때 서버 값(환경변수)만
바꾸면 바로 동작하도록 지금 만들어둔다.

## 결정 (Decision)

### 강제 업데이트 시 동작 — 완전 차단

클라이언트가 `forceUpdate: true`를 받으면 앱 사용 자체를 막는
전체 화면 업데이트 유도 UI를 띄운다(뒤로가기 불가). 안내만 하는
방식은 사용자가 무시하고 넘어갈 수 있어 강제 업데이트의 목적이
흐려진다.

### 플랫폼별 관리

iOS/Android가 서로 다른 배포 속도를 가질 수 있어(심사 지연 등),
`minimumVersion`/`updateUrl`을 플랫폼별로 분리해 환경변수로 관리한다.

```yaml
app:
  ios:
    minimum-version: ${IOS_MINIMUM_VERSION:1.0.0}
    update-url: ${IOS_UPDATE_URL:}
  android:
    minimum-version: ${ANDROID_MINIMUM_VERSION:1.0.0}
    update-url: ${ANDROID_UPDATE_URL:}
```

### API — 인증 불필요, 절대 실패하지 않음

```
GET /api/v1/app/version-check?platform=IOS|ANDROID&version=1.0.0
```

**인증 불필요**: 앱 실행 시 로그인 화면 뜨기도 전에 호출돼야 하므로
`SecurityConfig`의 `permitAll` 대상에 `/app/version-check` 추가
(`/auth/**`와 동일 예외 처리).

**`platform`을 enum으로 자동 바인딩하지 않는다.** 지금까지(이슈
#16) 확립한 "enum 파라미터가 잘못 오면 400"이라는 전역 규칙과
정반대로, **이 API는 뭐가 와도 400을 주지 않고 안전하게 폴백**해야
한다 — 앱 실행 자체를 막을 수 있는 API라 실패를 허용할 수 없다.
따라서 `platform`/`version` 둘 다 `String`으로 받아 서비스 레이어에서
직접, 관대하게 파싱한다.

```
platform이 "IOS"/"ANDROID"(대소문자 무시) 외의 값, null, 빈 문자열
  → 어느 플랫폼인지 알 수 없으므로 forceUpdate:false,
    minimumVersion:null, updateUrl:null로 안전하게 응답

version이 "1.0.0" 형식이 아니거나 파싱 실패
  → forceUpdate:false로 폴백(예외를 던지지 않고 catch)
```

### 버전 비교 — 점(.) 단위 숫자 비교, 문자열 비교 금지

`"1.10.0" < "1.2.0"`처럼 문자열 사전순 비교는 틀린 결과를 낸다
(문자열로는 "1.10.0" < "1.2.0"). `.`로 쪼개 각 자리를 숫자로
비교해야 한다.

```java
public final class VersionComparator {

    private VersionComparator() {
    }

    public static boolean isLowerThan(String version, String minimumVersion) {
        int[] v = parse(version);
        int[] min = parse(minimumVersion);
        for (int i = 0; i < 3; i++) {
            if (v[i] != min[i]) {
                return v[i] < min[i];
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }
}
```

`parse()`가 던질 수 있는 예외(`NumberFormatException` 등)는
`AppVersionService`에서 감싸 `forceUpdate:false`로 폴백한다 —
`VersionComparator` 자체는 예외를 던져도 되고(순수 유틸이라 방어는
호출부 책임), 호출부(Service)가 "이 API는 절대 실패하지 않는다"는
계약을 지킨다.

### 응답 DTO

```java
public record VersionCheckResponse(boolean forceUpdate, String minimumVersion, String updateUrl) {
    public static VersionCheckResponse of(boolean forceUpdate, String minimumVersion, String updateUrl) {
        return new VersionCheckResponse(forceUpdate, minimumVersion, updateUrl);
    }
}
```

### Service — 생성자 주입으로 환경변수 4개 수령

```java
@Service
public class AppVersionService {

    private final String iosMinimumVersion;
    private final String iosUpdateUrl;
    private final String androidMinimumVersion;
    private final String androidUpdateUrl;

    public AppVersionService(
            @Value("${app.ios.minimum-version}") String iosMinimumVersion,
            @Value("${app.ios.update-url}") String iosUpdateUrl,
            @Value("${app.android.minimum-version}") String androidMinimumVersion,
            @Value("${app.android.update-url}") String androidUpdateUrl) {
        this.iosMinimumVersion = iosMinimumVersion;
        this.iosUpdateUrl = iosUpdateUrl;
        this.androidMinimumVersion = androidMinimumVersion;
        this.androidUpdateUrl = androidUpdateUrl;
    }

    public VersionCheckResponse checkVersion(String platform, String version) {
        if (!"IOS".equalsIgnoreCase(platform) && !"ANDROID".equalsIgnoreCase(platform)) {
            return VersionCheckResponse.of(false, null, null);
        }
        boolean isIos = "IOS".equalsIgnoreCase(platform);
        String minimumVersion = isIos ? iosMinimumVersion : androidMinimumVersion;
        String updateUrl = isIos ? iosUpdateUrl : androidUpdateUrl;
        boolean forceUpdate = isForceUpdateNeeded(version, minimumVersion);
        return VersionCheckResponse.of(forceUpdate, minimumVersion, updateUrl);
    }

    private boolean isForceUpdateNeeded(String version, String minimumVersion) {
        try {
            return VersionComparator.isLowerThan(version, minimumVersion);
        } catch (Exception e) {
            return false; // 절대 실패하지 않는다는 계약
        }
    }
}
```

**필드 주입(`@Autowired` 필드)이 아니라 생성자 주입으로 `@Value` 4개를
받는다** — `GeminiImageAnalysisProvider`와 동일 패턴, 최근 추가한
검증 스크립트 규칙11(필드 주입 금지)과도 일치.

### 패키지 위치 — 신규 `cmc.recap.app` 패키지

`/app`은 캡처/유저/인증 어디에도 속하지 않는 독립 개념이라, `Report`
때처럼 신규 최상위 패키지(`cmc.recap.app`)를 만든다.
`AppController`/`AppVersionService`/`VersionComparator`/
`VersionCheckResponse`를 여기 둔다.

## 고려한 대안 (Considered Options)

1. **`platform`을 enum으로 자동 바인딩 (기각)** — 이슈 #16에서
   확립한 "enum 바인딩 실패 시 400" 전역 규칙과 이 API가 요구하는
   "절대 실패 안 함" 계약이 정면 충돌. 문자열로 받아 수동 처리.
2. **문자열 그대로 버전 비교 (기각)** — "1.10.0" vs "1.2.0"에서
   틀린 결과를 냄.
3. **안내만 하고 계속 사용 허용 (기각)** — 사용자가 무시할 수 있어
   강제 업데이트 목적이 흐려짐.

## 결과 (Consequences)

### 긍정
- 앱 실행 초기(로그인 전)에도 안전하게 호출 가능.
- 잘못된 파라미터가 와도 앱이 죽거나 막히지 않음.
- 환경변수만 바꾸면 코드 배포 없이 강제 업데이트 활성화 가능.

### 부정 / 트레이드오프
- `platform` enum 자동 검증을 포기해, 오타 등 클라이언트 버그가
  있어도 서버가 조용히 무해하게 처리해버려 클라이언트 쪽에서
  발견이 늦어질 수 있음 — 다만 이 API의 목적(앱을 절대 막지 않음)과
  트레이드오프 관계.

## 후속 / 미결정

- [ ] 실제 강제 업데이트 발동 시점에 `IOS_UPDATE_URL`/
      `ANDROID_UPDATE_URL` 실제 스토어 링크 값 채우기 필요(지금은
      빈 문자열 기본값)
