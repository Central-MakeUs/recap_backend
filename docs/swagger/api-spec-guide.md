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

`ApiErrorCodes`, `ErrorCodeOperationCustomizer`는 `cmc.recap.global.swagger`
패키지에 위치한다 (구현 코드는 별도 파일 참고).