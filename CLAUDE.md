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

## 코드 규칙
- JPA AttributeConverter/EntityListener에 생성자 DI 금지 (ADR-0005)
- 시간은 Instant + UTC (ADR-0006). LocalDateTime 신규 사용 금지.
- 엔티티는 @NoArgsConstructor(PROTECTED) + 정적 팩토리 메서드 패턴 유지.

## 커밋/PR (RECAP 기존 컨벤션)
- 커밋: `type: 한글 제목(#이슈번호)` (feat, fix, docs, refactor, chore ...)
- 브랜치: `type#이슈번호`
- PR 본문은 diff와 문서에 있는 사실만 쓴다. 하지 않은 테스트를 통과로 쓰지 않는다.

## 금지
- main 직접 작업 / .env·시크릿 커밋 / 사용자 확인 없는 커밋·push