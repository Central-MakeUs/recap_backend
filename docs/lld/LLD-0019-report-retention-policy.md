# LLD-0019: 신고 데이터 보관 기간 정책

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-31 |
| 관련 | [LLD-0010](LLD-0010-inappropriate-content-report.md), [ADR-0015](../adr/ADR-0015-user-withdrawal-hybrid-deletion.md), [LLD-0012](LLD-0012-image-expiration-batch.md) |

## 맥락 (Context)

LLD-0010에서 `Report`를 `InfoCard`/회원탈퇴와 독립적으로 무기한
보관하도록 설계했으나, "얼마나 보관할지"는 후속 미결정으로 남겨뒀다.
개인정보처리방침 작성 중 이 부분이 App Store 심사 리스크(Apple
가이드라인 5.1.1(v) — 계정 삭제 시 연관 데이터도 삭제해야 한다는
원칙)와 연결된다는 게 확인되어, 보관 기간을 확정한다.

## 결정 (Decision)

**"신고 접수일로부터 1년" 또는 "회원 탈퇴 시" 중 먼저 도래하는
시점에 삭제**한다.

```
탈퇴 시 즉시 삭제 → UserService.withdraw()에 한 줄 추가
1년 경과 시 삭제 → 매일 도는 배치(LLD-0012 패턴 재사용)
```

### 회원탈퇴 연동

```java
// UserService.java
@Transactional
public void withdraw(Long userId) {
    User user = getUser(userId);
    deleteAllCaptures(user);
    refreshTokenRepository.deleteByUser(user);
    reportRepository.deleteByUser(user); // 신규 추가
    user.withdraw();
}
```

`ReportRepository`에 벌크 삭제 쿼리 추가:

```java
@Modifying
@Query("delete from Report r where r.user = :user")
void deleteByUser(@Param("user") User user);
```

### 1년 경과 자동 삭제 배치

`InfoCard` 자동 만료(LLD-0012)와 달리, `Report` 삭제는 S3 등 외부
시스템을 전혀 안 건드리는 순수 DB 삭제라 **개별 row 단위 try-catch
없이 벌크 DELETE 하나로 충분**하다(외부 API 호출이 없어 부분 실패
자체가 발생할 수 없음).

```java
@Component
@RequiredArgsConstructor
public class ReportExpirationScheduler {

    private static final Period RETENTION_PERIOD = Period.ofYears(1);

    private final ReportRepository reportRepository;

    @Scheduled(cron = "0 30 4 * * *") // 매일 04:30 (이미지 만료 배치 04:00과 겹치지 않게 분산)
    @Transactional
    public void expireOldReports() {
        Instant cutoff = Instant.now().atZone(ZoneOffset.UTC).minus(RETENTION_PERIOD).toInstant();
        reportRepository.deleteByCreatedAtBefore(cutoff);
    }
}
```

`Instant.minus(Period)`는 `Period`가 `YEARS`/`MONTHS` 단위를 쓰는 경우
`UnsupportedTemporalTypeException`을 던진다(`Instant`는 날짜 기반 단위를
지원하지 않음). `OriginalImageExpirationScheduler`와 동일하게
`ZonedDateTime`으로 변환 후 계산한다.

```java
// ReportRepository.java
@Modifying
@Query("delete from Report r where r.createdAt < :cutoff")
void deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
```

### `@EnableScheduling`

이미 LLD-0012에서 활성화되어 있으므로 추가 설정 불필요.

## 고려한 대안 (Considered Options)

1. **LLD-0012처럼 개별 row 조회 후 반복 삭제 (기각)** — `Report`
   삭제는 S3 등 외부 시스템 호출이 전혀 없어, 부분 실패를 걱정할
   이유가 없다. 벌크 DELETE 하나로 충분해 불필요한 복잡도.
2. **탈퇴 시엔 삭제 안 하고 1년 배치에만 맡기기 (기각)** — Apple
   심사 리스크(가이드라인 5.1.1(v))를 낮추려는 목적 자체를 못
   달성함.

## 결과 (Consequences)

### 긍정
- App Store 심사에서 "계정 삭제 시 연관 데이터 미삭제"로 지적받을
  리스크가 크게 낮아짐.
- 외부 시스템 의존이 없어 구현이 단순함(벌크 쿼리 2개, 스케줄러 1개).

### 부정 / 트레이드오프
- 탈퇴한 유저의 신고 데이터가 즉시 사라져, LLD-0010의 원래 목적
  (신고 패턴 집계 분석)에서 그만큼의 데이터가 빠짐 — 다만 전체
  유저 대비 탈퇴 비율은 낮을 것으로 예상되어 집계 분석 자체의
  유효성에 큰 영향은 없을 것으로 판단.

## 후속 / 미결정

- 없음.
