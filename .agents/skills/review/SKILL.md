---
name: review
description: 'implement 스킬로 작성한 코드를 커밋 전 자체 검수한다. LLD/컨벤션과의 정합성을 확인하고 APPROVE 또는 REQUEST_CHANGES를 판정한다.'
---

# review

구현된 코드가 LLD, ADR, `docs/conventions/domain-design-principles.md`,
`docs/swagger/api-spec-guide.md`와 정합한지 자체 검수한다. implement
스킬의 마지막 단계에서 항상 실행된다.

## 핵심 원칙

- 실제 diff를 읽고 판단한다. 읽지 않고 통과시키지 않는다.
- 판정은 `APPROVE` 또는 `REQUEST_CHANGES` 둘 중 하나로 명확히 내린다.
- `REQUEST_CHANGES`면 무엇을 왜 고쳐야 하는지 구체적으로 적는다
  ("더 좋아 보인다" 같은 모호한 코멘트 금지).

## 점검 항목

### LLD 정합성
- [ ] 구현된 API 경로/메서드가 LLD의 엔드포인트 표와 일치하는가
- [ ] DTO 필드가 LLD와 일치하는가
- [ ] LLD `## 후속 / 미결정`에 있던 항목이 임의로 구현에 반영되지 않았는가

### 도메인 설계 원칙 (docs/conventions/domain-design-principles.md)
- [ ] 엔티티가 `new`로 직접 생성 가능한 상태로 열려 있지 않은가
- [ ] 생성/변경 메서드에 검증이 있는가
- [ ] 도메인 규칙 위반 시 `BusinessException(ErrorCode)`를 던지는가
  (원시 `IllegalStateException`/`IllegalArgumentException` 사용 금지)
- [ ] setter가 없는가
- [ ] 서비스 레이어에 `new XxxResponse(` 형태가 없는가 (`from()`/`of()` 사용)
- [ ] private 메서드가 public 메서드들 사이에 끼어 있지 않고 맨 아래에
  모여 있는가 (스크립트 규칙6은 휴리스틱이라 오탐 가능 — 직접 눈으로
  한 번 더 확인)

### 문서화 (docs/swagger/api-spec-guide.md)
- [ ] 컨트롤러에 Swagger 애너테이션이 남아있지 않은가 (`{Domain}ApiDocs`
  인터페이스로 분리되어 있는가)
- [ ] `@ApiErrorCodes`에 나열한 코드가 실제로 그 메서드에서 발생 가능한가

### 레이어링
- [ ] Controller가 Repository를 직접 import하지 않는가
- [ ] JPA `AttributeConverter`/`EntityListener`류에 생성자 DI가 없는가
  (ADR-0005)

### 테스트
- [ ] 인수조건에 대응하는 테스트가 있는가
- [ ] `@DisplayName`이 "~하면/할 때 ~한다" 형식인가

## 절차

1. `git diff`(또는 이번 구현 세션의 변경 파일 전체)를 읽는다.
   1-1. 변경된 각 `.java` 파일에 대해 `bash scripts/harness/validate-java-rules.sh <파일>`을
   실행해 기계적 규칙 위반을 먼저 걸러낸다.
2. 관련 LLD, ADR을 다시 연다.
3. 위 점검 항목을 하나씩 확인한다.
4. 위반이 없으면 `APPROVE`, 있으면 `REQUEST_CHANGES` + 구체적 수정 목록을
   사용자에게 보고한다.
5. `REQUEST_CHANGES`인 경우 사용자 확인 후 수정, 재검수한다.

## 하지 말 것

- diff를 읽지 않고 APPROVE.
- "대체로 괜찮아 보인다" 식의 모호한 판정.
- 점검 항목에 없는 개인 취향(변수명 등)을 이유로 REQUEST_CHANGES.