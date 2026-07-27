# LLD-0014: 최근 정리된 캡처 전체 목록 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-28 |
| 관련 | [LLD-0004](LLD-0004-home-summary-api.md), [LLD-0006](LLD-0006-storage-api.md), [LLD-0012](LLD-0012-image-expiration-batch.md) |

## 맥락 (Context)

02-02 화면 설계서의 "전체 최신"(홈의 "최근 정리된 스크린샷" 미리보기
옆 `>` 화살표로 진입하는 전체 목록 화면)을 스프린트 초반 후순위로
미뤄뒀던 것을 구현한다. 홈의 미리보기(`GET /home/summary`의
`recentCaptures`, 최대 3개, LLD-0004)와는 별개의 엔드포인트다 —
LLD-0004는 변경하지 않는다.

## 결정 (Decision)

### 30일 제한 유지

홈 미리보기와 동일하게 최근 30일 이내 정리 완료된 캡처만 대상으로
한다. 30일이 지난 캡처는 보관함에서 확인한다(02-02 설계서 원래
의도).

이 제한은 LLD-0012(원본 이미지 자동 만료) 이후 더 명확한 의미를
갖는다 — `InfoCard`는 만료돼도 영구 보존되므로(이미지만 삭제),
30일 지난 캡처도 "존재"는 계속 하지만 이 화면엔 노출하지 않고
보관함으로 유도한다. 결제 등으로 개인별 보관 기간이 달라질 가능성도
있어, 고정 상수보다 유연하게 남겨둘 여지를 유지한다(지금은 상수
그대로).

### 페이지네이션 — 보관함과 동일 기준(기본 20)

```
GET /api/v1/home/recent-captures?page=0&size=20
```

정렬 옵션은 없다(최신순 고정) — "최근"이라는 화면 성격상 정렬
토글이 불필요.

### 응답 DTO — 신규 `CapturePageResponse`

```java
public record CapturePageResponse(long count, boolean hasNext, List<CaptureSummaryResponse> items) {
    public static CapturePageResponse of(long count, boolean hasNext, List<CaptureSummaryResponse> items) {
        return new CapturePageResponse(count, hasNext, items);
    }
}
```

`CaptureListResponse`(LLD-0006, 보관함용)는 페이지네이션이 없는
전체 목록 응답이라 재사용하지 않는다. `SearchResponse`(LLD-0007)와
구조는 같지만(count/hasNext/items), 검색 전용 타입이라 별도로
만든다.

### 패키지 위치 — `HomeService`/`HomeController`에 추가

이 화면은 홈의 "최근 정리된 스크린샷" 섹션에서 진입하는 화면이라
`HomeController`/`HomeApiDocs`에 엔드포인트를 추가하고, 기존
`HomeService`의 30일 기준 상수(`RECENT_DAYS`)를 그대로 재사용한다.
새 컨트롤러를 만들지 않는다.

### 신규 Repository 메서드

```java
Page<InfoCard> findByUserAndCreatedAtAfter(User user, Instant since, Pageable pageable);
```

### Service 로직

```java
public CapturePageResponse getRecentCapturesPage(Long userId, int page, int size) {
    User user = userRepository.getReferenceById(userId);
    Instant since = Instant.now().minus(RECENT_DAYS, ChronoUnit.DAYS);
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<InfoCard> result = infoCardRepository.findByUserAndCreatedAtAfter(user, since, pageable);
    List<CaptureSummaryResponse> items = result.getContent().stream()
            .map(this::toCaptureSummary) // 기존 private 헬퍼 재사용
            .toList();
    return CapturePageResponse.of(result.getTotalElements(), result.hasNext(), items);
}
```

## 고려한 대안 (Considered Options)

1. **30일 제한 제거 (기각)** — LLD-0012 이후 `InfoCard`가 영구
   보존되므로, 제한을 없애면 이 화면과 보관함의 역할 구분이 사라짐.
2. **`CaptureListResponse` 재사용 (기각)** — 페이지네이션 정보
   (`hasNext`)가 없는 구조라 이번 요구사항과 안 맞음.
3. **별도 컨트롤러 신설 (기각)** — 홈 화면에서 진입하는 화면이라
   `HomeController`에 두는 게 자연스러움, 30일 상수도 이미 거기 있음.

## 결과 (Consequences)

### 긍정
- 기존 `HomeService`의 30일 로직·`toCaptureSummary()` 헬퍼 재사용으로
  신규 코드 최소화.
- 보관함과 페이지 크기 기준을 통일해 클라이언트 일관성 확보.

### 부정 / 트레이드오프
- 없음.

## 후속 / 미결정

- [ ] 결제 등으로 개인별 보관 기간이 달라지면, `RECENT_DAYS` 상수를
      유저별 설정값으로 바꿔야 할 수 있음 — 지금은 고정 상수 유지
