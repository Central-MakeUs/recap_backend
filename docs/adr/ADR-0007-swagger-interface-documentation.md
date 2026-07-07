# ADR-0007: Swagger 문서화 — 인터페이스 분리 및 에러 응답 자동 문서화

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-08 |
| 관련 | ErrorCode, GlobalExceptionHandler, docs/swagger/api-spec-guide.md |

## 맥락 (Context)

RECAP은 springdoc(애너테이션 기반) 방식으로 API 문서를 생성한다. 컨트롤러에
직접 `@Operation`, `@ApiResponse` 등을 붙이면 다음 문제가 생긴다.

- 비즈니스 로직과 문서화 애너테이션이 뒤섞여 컨트롤러 가독성이 떨어진다.
- 에러 응답 문서를 각 엔드포인트마다 수기로 나열하면, `ErrorCode`가 추가/
  변경될 때마다 여러 컨트롤러의 문서를 일일이 갱신해야 하고 누락 위험이 크다.
- RECAP은 이미 `ErrorCode` enum과 `GlobalExceptionHandler`(`ApiResponse.failure`
  기반 통일된 에러 응답)를 갖추고 있어, 에러 문서를 자동 생성할 수 있는
  기반이 마련되어 있다.

참고한 타 프로젝트(팀원 제공)는 컨트롤러와 문서화 애너테이션을 인터페이스로
분리하고, `@ApiErrorCodes` 커스텀 애너테이션 + `OperationCustomizer`로
에러 응답 문서를 자동 생성하는 구조를 쓰고 있었다. 다만 해당 프로젝트는
RFC 7807 Problem Details 형식을 쓰는 반면, RECAP은 `ApiResponse` 자체
포맷(`success`/`data`/`error{code,message}`)을 쓰므로 그대로 이식할 수 없다.

## 결정 (Decision)

- 컨트롤러는 `{Domain}Controller implements {Domain}ApiDocs` 구조로 작성한다.
  `{Domain}ApiDocs` 인터페이스에 `@Tag`, `@Operation`, `@ApiResponses`,
  `@ApiErrorCodes`를 선언하고, 컨트롤러 구현체는 `@Override`만 하고 문서화
  애너테이션을 붙이지 않는다.
- `@ApiErrorCodes(ErrorCode...)` 커스텀 애너테이션과 `ErrorCodeOperationCustomizer`
  (`OperationCustomizer` 구현체)로, 메서드에 나열된 `ErrorCode`를 HTTP 상태별로
  그룹화해 Swagger 에러 응답 예시를 자동 생성한다.
- 에러 응답 예시는 RECAP의 실제 응답 포맷(`ApiResponse.failure` 구조)을
  그대로 반영한다. RFC 7807 포맷은 채택하지 않는다.
- `ErrorCodeOperationCustomizer`는 순수 POJO이며 JPA가 생명주기를 관리하는
  컴포넌트가 아니므로, ADR-0005의 DI 배제 원칙과 무관하게 일반
  스프링 빈(`@Bean`)으로 등록한다.

## 고려한 대안 (Considered Options)

1. **컨트롤러에 직접 애너테이션 (기각)** — 구현이 단순하지만 비즈니스 로직과
   문서화 관심사가 섞이고, 에러 응답을 매 엔드포인트마다 수기로 나열해야
   해서 `ErrorCode` 변경 시 문서 누락 위험이 크다.
2. **인터페이스 분리 + 수동 `@ApiResponse` 나열 (기각)** — 관심사는
   분리되나 에러 응답 자동화가 없어 여전히 수기 나열 문제가 남는다.
3. **인터페이스 분리 + `@ApiErrorCodes` 자동화 (채택)** — 관심사 분리와
   에러 문서 자동 생성을 모두 얻는다. `ErrorCode`에 새 항목이 추가되면
   해당 항목을 사용하는 메서드의 애너테이션 배열에만 추가하면 되고,
   HTTP 상태 분류와 예시 JSON 생성은 커스터마이저가 전담한다.

## 결과 (Consequences)

### 긍정
- 컨트롤러가 라우팅/위임에만 집중해 가독성이 높아진다.
- 에러 응답 문서가 실제 `ErrorCode` 정의와 항상 일치한다(단일 소스).
- 새 에러 코드 추가 시 문서 갱신 누락 위험이 사실상 사라진다.

### 부정 / 트레이드오프
- 컨트롤러 1개당 인터페이스 1개가 추가되어 파일 수가 늘어난다.
- 인터페이스의 메서드 시그니처와 구현체의 `@Override`가 어긋나면(파라미터
  순서 등) 컴파일 시점에는 잡히지만 리뷰 시 주의가 필요하다.
- 참고 프로젝트의 RFC 7807 방식이 아니라 RECAP 자체 포맷을 반영해야 하므로,
  향후 유사 참고 코드를 가져올 때마다 응답 포맷 차이를 확인하는 절차가
  필요하다(본 ADR과 `docs/swagger/api-spec-guide.md`가 그 기준점 역할).

## 후속 / 미결정
- [ ] `ApiResponse` 클래스의 정확한 필드 구조(성공 응답 포함)를 문서에
      반영해 예시 JSON을 더 정교화할 필요가 있는지 검토
