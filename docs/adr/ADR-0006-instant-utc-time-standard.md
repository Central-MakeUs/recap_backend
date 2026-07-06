# ADR-0006: 시간 표준 — Instant 타입과 UTC 고정

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-07 (소급 기록 — 실제 결정은 2026-06 하순) |
| 관련 | BaseTimeEntity, JpaAuditingConfig, Dockerfile |

## 맥락 (Context)

- iOS/Android 클라이언트가 서로 다른 기기 시간대를 가질 수 있다.
- 스크린샷의 시간순 정렬(최신순이 기본 정렬)이 핵심 기능이므로 시간 비교의
  정확성이 중요하다.
- 로컬 개발(KST)과 운영 서버 환경 간 시간 불일치가 발생하면 데이터가 오염된다.
- 클라우드 백업 등 추후 기능에서 서버 저장 시간 기준이 명확해야 한다.

## 결정 (Decision)

- 엔티티 시간 필드는 **`Instant`** 를 사용한다 (`BaseTimeEntity`의
  `createdAt`, `modifiedAt` — `@CreatedDate`/`@LastModifiedDate` 감사 필드).
- UTC를 3중으로 고정한다.
  1. JVM: Dockerfile `ENTRYPOINT`에 `-Duser.timezone=UTC`
  2. Hibernate: `hibernate.jdbc.time_zone=UTC`
  3. (배포 컨테이너 환경변수 `TZ: UTC`)
- `@EnableJpaAuditing`은 main 클래스가 아닌 별도 `JpaAuditingConfig`에
  둔다 — `@WebMvcTest` 슬라이스 테스트에서 JPA 빈 로드 충돌을 피하기 위함.

## 고려한 대안 (Considered Options)

1. **Instant (채택)** — UTC 단일 기준점. 어느 기기·리전에서든 비교가 안전.
   사람이 읽기 불편한 것은 표시 계층의 변환 책임으로 분리.
2. **LocalDateTime** — 직관적이나 시간대 정보가 없어 서버 타임존 설정에
   의존. 환경 간 설정이 어긋나면 조용히 데이터가 오염되는 위험.
3. **ZonedDateTime** — 시간대 보존은 완벽하나 MySQL TIMESTAMP가 시간대를
   저장하지 않아 별도 처리가 필요하고, 현 요구사항에는 오버스펙.

## 결과 (Consequences)

### 긍정
- 기기/서버/리전이 달라도 시간 비교·정렬이 항상 신뢰 가능.
- 환경별 타임존 차이로 인한 버그를 설정 3중 고정으로 원천 차단.

### 부정 / 트레이드오프
- KST 등 사용자 시간대 표시는 클라이언트(또는 응답 변환 계층)의 책임으로
  이동 — FE와 표시 규약 합의 필요.
- 유형별 요약 record의 `LocalDate`/`LocalTime`(예: 일정의 날짜·시각)은
  "시각 정보가 없는 값" 그 자체이므로 Instant 대상이 아님. 감사 필드
  (생성/수정 시각)와 도메인 값(예약 날짜)의 성격 구분을 유지해야 한다.

## 후속 / 미결정
- [ ] API 응답에서 시간 직렬화 포맷(ISO-8601 UTC) FE 합의 명문화
