# ADR (Architecture Decision Records)

중요한 아키텍처/기술 의사결정을 기록한다.
새 ADR은 `../templates/adr.md`를 복사해 `ADR-XXXX-<slug>.md`로 만든다.

작성 기준:
- 방향이 갈리는 결정(대안이 존재했던 결정)만 ADR로 남긴다.
- "고려한 대안"에는 실제로 검토했던 선택지와 기각 이유를 기록한다.
- 결정이 뒤집히면 문서를 수정하지 않고 새 ADR을 만들고 기존 문서를
  `Superseded by ADR-YYYY`로 표기한다.
- ADR-0001~0006은 하네스 도입(2026-07-07) 시점에 기결정 사항을 소급
  기록한 것으로, 날짜 항목에 실제 결정 시점을 병기한다.

| 번호 | 제목 | 상태 | 날짜 |
| --- | --- | --- | --- |
| [ADR-0001](ADR-0001-deviceid-anonymous-identity.md) | deviceId 기반 익명 식별 및 OAuth 병합 전략 | Accepted | 2026-07-07 |
| [ADR-0002](ADR-0002-card-summary-json-polymorphism.md) | 카드 요약 필드의 JSON 컬럼 + 다형성 컨버터 저장 | Accepted | 2026-07-07 |
| [ADR-0003](ADR-0003-no-original-image-storage.md) | 원본 스크린샷 이미지 미저장 (텍스트 요약 완결) | Accepted | 2026-07-07 |
| [ADR-0004](ADR-0004-amd64-instance-architecture.md) | 빌드-런타임 아키텍처 일치를 위한 amd64 EC2 채택 | Accepted | 2026-07-07 |
| [ADR-0005](ADR-0005-jpa-component-di-exclusion.md) | JPA 생명주기 관리 컴포넌트의 스프링 DI 배제 원칙 | Accepted | 2026-07-07 |
| [ADR-0006](ADR-0006-instant-utc-time-standard.md) | 시간 표준 — Instant 타입과 UTC 고정 | Accepted | 2026-07-07 |
| [ADR-0007](ADR-0007-swagger-interface-documentation.md) | Swagger 문서화 — 인터페이스 분리 및 에러 응답 자동 문서화 | Accepted | 2026-07-08 |
