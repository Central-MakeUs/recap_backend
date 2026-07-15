---
name: implement
description: 'LLD를 기준으로 코드를 실제로 구현한다. 사용자가 "구현해줘", "코드 짜줘", "LLD대로 만들어줘" 등을 요청할 때 사용.'
---

# implement

LLD를 기반으로 코드를 구현한다. 구현 완료 후 반드시 **review 스킬을
이어서 실행**해 자체 검수한다.

## 핵심 원칙

- LLD가 없으면 구현을 시작하지 않는다. LLD 작성을 먼저 요청한다.
- LLD의 `## 후속 / 미결정`이 비어 있지 않으면 해당 항목을 사용자에게
  확인하고 진행한다. 추측으로 채우지 않는다.
- `CLAUDE.md`, `docs/conventions/domain-design-principles.md`,
  `docs/swagger/api-spec-guide.md`의 규칙을 함께 따른다.
- 구현 단위는 LLD 하나 = PR 하나를 원칙으로 한다.
- 문서 확인이 필요해도 자체적으로 웹 검색하지 않는다. 무엇을 왜 확인해야 하는지 먼저 보고하고 허가를 받는다.
- STAGE 중간 보고에는 코드 전문 대신 변경 파일 경로와 한두 줄 요약만 남긴다.

## 절차

1. 관련 LLD를 `docs/lld/`에서 읽는다.
2. 관련 ADR을 `docs/adr/`에서 확인한다 (해당 LLD의 "관련" 항목 참고).
3. LLD의 `## 후속 / 미결정`을 확인하고, 미결정 항목이 있으면 사용자에게
   알린 뒤 답을 받고 진행한다.
4. LLD `## 결정 (Decision)`의 API/DTO/엔티티 설계를 기준으로 구현 계획을
   세운다.
5. 계획을 사용자에게 먼저 보고하고 승인을 받는다.
6. 코드를 작성한다. 작성 중 `docs/conventions/domain-design-principles.md`의
   체크리스트를 따른다 (자가 검증 생성자, BusinessException 사용,
   setter 금지, Controller의 Repository 직접 참조 금지 등).
   6-1. 파일 작성/수정 후 `bash scripts/harness/validate-java-rules.sh <파일경로>`를
   실행해 기계적으로 검사 가능한 규칙 위반이 없는지 확인한다.
7. 신규 API에는 `docs/swagger/api-spec-guide.md` 규칙대로 `{Domain}ApiDocs`
   인터페이스와 `@ApiErrorCodes`를 작성한다.
8. 테스트를 작성한다 (아래 "테스트 작성 규칙" 참고).
9. 구현 완료 후 **review 스킬을 실행**한다.
10. review 결과 수정이 필요하면 반영 후 다시 review를 실행한다.
11. review를 통과하면 commit 스킬로 커밋한다.

## 테스트 작성 규칙

- JUnit 5 + `@ExtendWith(MockitoExtension.class)`
- BDDMockito(`given/willReturn`) 사용
- 메서드명: 한글 언더스코어 (예: `deviceId_없이_생성하면_예외를_던진다`)
- `@DisplayName`: `<행위>하면/할 때 <결과>한다` 형식
- 상태 검증(`assertEquals`, `assertThat`)을 우선하고, 외부 호출·이벤트
  발행·삭제 같은 side effect만 `verify`로 검증한다

### 테스트 작성 시 토큰 절약 규칙

- 스타일을 맞추려고 기존 테스트 파일 전체를 다시 읽지 않는다. 아래
  템플릿을 그대로 따르면 충분하다:

  ```java
  @ExtendWith(MockitoExtension.class)
  class {ClassName}Test {
      @Mock
      private {Dependency} {dependency};
      private {ClassName} sut;

      @BeforeEach
      void setUp() {
          sut = new {ClassName}({dependency}, ...);
      }

      @Nested
      @DisplayName("{메서드명}")
      class {MethodName} {
          @Test
          @DisplayName("{조건}하면 {결과}한다")
          void {한글_조건}_{한글_결과}() {
              // given
              given({dependency}.{method}(...)).willReturn(...);
              // when
              var result = sut.{method}(...);
              // then
              assertThat(result).{검증};
          }
      }
  }
  ```

- 테스트 실행 결과는 필터링해서만 컨텍스트에 남긴다. 전체 로그를
  그대로 붙이지 말고, 실패 시엔 `FAILED`/`Exception` 라인 주변만
  (예: `grep -A 10 "FAILED\|Exception"`), 성공 시엔 "N/N 통과" 한 줄만
  남긴다.
- 실행 명령 예: `./gradlew test --tests {ClassName} 2>&1 | grep -E
  "PASSED|FAILED|Exception"` (전체 stdout을 그대로 붙이지 않기 위함)
- 환경변수 문제로 로컬 테스트가 막히면, export로 임시 우회하지 말고
  `application-local.yml`에 기본값이 빠진 건 아닌지 먼저 확인한다
  (매 세션 재발견 방지).

## 엔티티 변경 시 추가 작업

- `docs/erd/`에 ERD 문서가 존재하면 함께 갱신한다 (현재 RECAP은
  ERD 문서가 아직 없음 — 생기면 이 규칙이 적용된다).
- 신규 도메인 규칙을 추가하면서 `docs/conventions/domain-design-principles.md`의
  원칙에서 벗어나는 예외적 판단을 했다면, 그 이유를 PR 본문에 남긴다.

## 하지 말 것

- LLD 없이 구현 시작.
- 미결정 사항을 임의로 결정하고 구현.
- review 스킬 없이 commit.
- LLD 범위 밖의 추가 기능 구현.
- 엔티티에 setter 추가, 원시 예외(`IllegalStateException` 등)로 도메인
  규칙 위반 처리, 서비스 레이어에서 `new XxxResponse(` 직접 생성.