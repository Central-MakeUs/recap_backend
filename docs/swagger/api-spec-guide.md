# API 문서화 정책 (Swagger / springdoc)

> 관련: [ADR-0007](../adr/ADR-0007-swagger-interface-documentation.md)

RECAP은 컨트롤러와 API 문서화 애너테이션을 **인터페이스로 분리**하고,
에러 응답 문서는 `@ApiErrorCodes`로 **자동 생성**한다.

## 1. 구조 규칙

```
{domain}/
├── controller/
│   ├── {Domain}Controller.java     ← 순수 구현체. 문서화 애너테이션 없음.
│   └── {Domain}ApiDocs.java        ← 인터페이스. 모든 Swagger 애너테이션은 여기에.
```

- 컨트롤러는 반드시 대응하는 `{Domain}ApiDocs` 인터페이스를 `implements` 한다.
- 컨트롤러 메서드에는 `@GetMapping` 등 매핑 애너테이션과 `@Override`만
  붙인다. `@Operation`, `@ApiResponses`, `@ApiErrorCodes`, `@Parameter`는
  전부 인터페이스 쪽에 선언한다.
- 인터페이스 최상단에 `@Tag(name = "...", description = "...")`로 도메인을
  선언한다.

## 2. 필수 애너테이션

| 위치 | 애너테이션 | 용도 |
| --- | --- | --- |
| 인터페이스 클래스 | `@Tag` | Swagger UI 그룹핑 |
| 인터페이스 메서드 | `@Operation(summary = "...")` | 한 줄 설명 (한글, 동사형) |
| 인터페이스 메서드 | `@ApiResponses` | 성공 응답만 기술 (200/201/204 등) |
| 인터페이스 메서드 | `@ApiErrorCodes({...})` | 실패 응답. 해당 메서드에서 발생 가능한 `ErrorCode`를 전부 나열 |
| 인터페이스 파라미터 | `@Parameter(hidden = true)` | 인증 식별자 등 클라이언트가 직접 넘기지 않는 파라미터에 사용 |

## 3. `@ApiErrorCodes` 사용 규칙

- 해당 엔드포인트에서 `GlobalExceptionHandler`를 거쳐 실제로 발생할 수
  있는 `ErrorCode`만 나열한다. "혹시 몰라서" 관련 없는 코드를 넣지 않는다.
- `INTERNAL_ERROR`는 원칙적으로 나열하지 않는다(모든 엔드포인트에서
  공통으로 발생 가능하므로 개별 문서화 실익이 낮다). 필요하다고 판단되면
  이 규칙 자체를 변경 논의한다.
- 새 `ErrorCode`를 추가했는데 어떤 메서드에서도 `@ApiErrorCodes`로
  참조하지 않는다면, 그 코드가 실제로 쓰이는 위치인지 다시 확인한다.

## 4. 에러 응답 예시 형식 (중요 — RECAP 고유 규칙)

**RFC 7807(Problem Details) 형식을 쓰지 않는다.** RECAP의 `GlobalExceptionHandler`가
실제로 반환하는 `ApiResponse.failure(...)` 구조를 그대로 예시로 생성한다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "유저를 찾을 수 없습니다"
  }
}
```

이 형식은 `ErrorCodeOperationCustomizer`가 `ErrorCode`로부터 자동 생성하므로,
컨트롤러/인터페이스 작성자가 직접 예시 JSON을 손으로 작성할 필요는 없다.

## 5. 체크리스트 (PR 리뷰 / AI 작업 시)

- [ ] 컨트롤러에 Swagger 애너테이션이 하나라도 남아있지 않은가
- [ ] 인터페이스 메서드 시그니처와 컨트롤러 `@Override` 시그니처가 일치하는가
- [ ] `@ApiErrorCodes`에 나열한 코드가 실제로 그 메서드에서 던져질 수 있는가
- [ ] 새 도메인 추가 시 `{Domain}ApiDocs` 인터페이스가 함께 생성됐는가
- [ ] `@Tag`가 인터페이스에 한 번만 선언되어 있는가 (컨트롤러에 중복 선언 금지)

## 6. 참고 구현

`ApiErrorCodes`, `ErrorCodeOperationCustomizer`는 `cmc.recap.global.exception`
패키지에 위치한다 (구현 코드는 별도 파일 참고).

## 7. 문서 상세도 기준 — 어디를 자세히 쓰고 어디는 자동화에 맡길지

문서화가 인터페이스로 분리되어 구현체 가독성에 영향을 주지 않으므로,
**자세히 써도 되는 부분은 적극적으로 자세히 쓴다.** 다만 아래 구분은
반드시 지킨다.

```
자유롭게 상세히 작성 (자동화 대상 아님)
  - @Operation(summary=..., description=...) — summary 한 줄 넘어
    description으로 비즈니스 맥락까지 설명 가능
  - @Parameter(description=..., example=...) — path variable에
    허용값을 명시 (예: provider는 "kakao" 또는 "apple"만 허용)
  - DTO record 필드의 @Schema(description=, example=) — FE가 정확한
    값 형식을 문서만 보고 알 수 있게

절대 수동으로 되돌리지 않음 (ADR-0007로 자동화됨)
  - 에러 응답의 개별 @ApiResponse + @Content(schema=...) 나열
  - @ApiErrorCodes만 선언하면 설명·JSON 예시가 자동 생성된다
  - 성공 응답 바디 스키마도 메서드 리턴 타입에서 springdoc이 자동
    추론하므로, 별도로 @Content(schema=...)를 달 필요가 없다
```

### 예시

```java
@Operation(
        summary = "소셜 로그인/가입",
        description = "provider 토큰을 검증해 로그인 또는 신규 가입을 처리한다. "
                + "기존에 deviceId로 생성된 익명 유저가 있으면 계정을 병합한다."
)
@ApiResponses(@ApiResponse(responseCode = "200", description = "로그인/가입 성공"))
@ApiErrorCodes({
        ErrorCode.INVALID_INPUT,
        ErrorCode.OAUTH_VERIFICATION_FAILED,
        ErrorCode.ALREADY_LINKED_OAUTH
})
ResponseEntity<ApiResponse<TokenResponse>> login(
        @Parameter(description = "소셜 로그인 provider", example = "kakao")
        String provider,
        OAuthLoginRequest request
);
```

```java
public record OAuthLoginRequest(
        @Schema(description = "클라이언트가 생성한 기기 식별자", example = "550e8400-e29b-...")
        String deviceId,

        @Schema(description = "카카오는 access token, Apple은 identityToken(JWT) 원문")
        String providerToken,

        @Schema(description = "요청을 보낸 플랫폼")
        Platform platform
) {}
```

이 정도 밀도로 채우면, "문서화가 자세하면 좋겠다"는 목표를 에러 응답
자동화의 이점을 잃지 않으면서 달성할 수 있다.

## 8. 클래스 이름 충돌 처리 (FQN 인라인 참조 금지)

`{Domain}ApiDocs` 인터페이스는 RECAP 자체 `ApiResponse<T>`(공통 응답
래퍼)와 Swagger의 `@ApiResponse`(애너테이션)를 한 파일에서 동시에 쓴다.
둘 다 단순 이름이 `ApiResponse`라 Java는 import 별칭을 지원하지 않으므로
반드시 하나는 전체 경로(FQN)로 풀어써야 한다.

**규칙: 파일 안에서 더 자주 쓰이는 쪽을 import하고, 덜 쓰이는 쪽을
전체 경로로 남긴다.** `*ApiDocs` 파일에서는 리턴 타입마다 등장하는
RECAP 자체 `ApiResponse<T>`가 훨씬 자주 쓰이므로 이쪽을 import하고,
`@ApiResponses` 안에 한 번씩만 감싸지는 Swagger `@ApiResponse`를
전체 경로로 남긴다.

```java
import cmc.recap.global.dto.ApiResponse;

@io.swagger.v3.oas.annotations.responses.ApiResponses(
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
)
ResponseEntity<ApiResponse<TokenResponse>> login(...);
```

이 충돌을 피하겠다고 RECAP 자체 `ApiResponse` 클래스명을 바꾸지 않는다
— 이미 서비스·컨트롤러 등 다수 파일에서 쓰이는 핵심 타입이라, `*ApiDocs`
파일 몇 개의 가독성 문제를 위해 전체를 리팩터링하는 건 비용 대비 이득이
낮다.

일반 규칙으로 확장하면: **본문 코드에 FQN을 그대로 쓰지 않는다.**
반드시 필요한 경우(이런 이름 충돌)에만 예외로 허용하고, 그 경우에도
어느 쪽을 풀어쓸지는 위 기준(빈도가 낮은 쪽)을 따른다.