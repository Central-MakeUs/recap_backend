# CLAUDE.md — RECAP 백엔드

## 프로젝트
- 스택: Java 17 / Spring Boot 4.1.0 / Hibernate 7.4.1 / MySQL 8.0 (RDS)
- 빌드: ./gradlew build / 테스트: ./gradlew test
- 배포: main push 시 GitHub Actions 자동 배포. 직접 배포 명령 금지.

## 하네스 규칙 (필수)
- 아키텍처 결정은 docs/adr/ 이 근거다. 구현 전 관련 ADR을 읽는다.
- ADR과 충돌하는 구현이 필요하면 코드를 쓰지 말고 먼저 보고한다.
- 새 기능은 docs/lld/ 에 LLD 작성 후 구현한다. LLD가 없으면 작성을 먼저 제안한다.
- LLD의 "미결정 사항"은 추측으로 채우지 않는다. 반드시 질문한다.
- ADR/LLD에 근거해 미리 마련된 확장 필드나 구조(예: nullable 컬럼, 다형성 설계)는
    "요청받지 않은 유연성"이 아니라 팀이 이미 결정한 요청으로 간주하고 임의로 단순화하지 않는다.

## 코드 규칙
- JPA AttributeConverter/EntityListener에 생성자 DI 금지 (ADR-0005)
- 시간은 Instant + UTC (ADR-0006). LocalDateTime 신규 사용 금지.
- 엔티티는 @NoArgsConstructor(PROTECTED) + 정적 팩토리 메서드 패턴 유지.

## 커밋/PR (RECAP 기존 컨벤션)
- 커밋: `type: 한글 제목(#이슈번호)` (feat, fix, docs, refactor, chore ...)
- 브랜치: `type#이슈번호`
- PR 본문은 diff와 문서에 있는 사실만 쓴다. 하지 않은 테스트를 통과로 쓰지 않는다.

## 커밋 정책
- docs/ 하위 문서(ADR, LLD, policy, 템플릿) 변경: 확인 없이 자율 커밋 가능
    - 커밋 메시지: `docs/#이슈번호: 한글 제목`, 연결 이슈가 없으면 `docs: 한글 제목`
- 그 외(소스 코드, 설정, CI, CLAUDE.md 자체): 커밋 전 사용자 확인 필수
- push와 main 직접 작업은 항상 사용자 확인 필요

## 금지
- .env·시크릿 커밋
- 하지 않은 테스트를 통과한 것으로 기술