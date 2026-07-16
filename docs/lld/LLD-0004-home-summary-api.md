# LLD-0004: 홈 화면 API (GET /home/summary)

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-16 |
| 관련 | [LLD-0002](LLD-0002-capture-upload-organize-explore.md), 이슈 ④ |

## 맥락 (Context)

02-01(홈) 화면은 최근 정리된 캡처, 즐겨찾기, 자주 저장한 유형을 한 화면에
보여준다. 이슈 본문엔 "무엇을 보여줄지"만 정의되어 있고 쿼리 방식은
정해지지 않아, 아래 3가지를 이번 LLD에서 확정한다.

## 결정 (Decision)

### 응답 구조

```json
{
  "recentCaptures": [ /* CaptureSummaryResponse 배열, 최대 3개 */ ],
  "favorites": [ /* CaptureSummaryResponse 배열, 최대 3개 */ ],
  "topTypes": [
    { "typeCode": "SHOPPING", "count": 12, "representativeThumbnailUrl": "..." }
  ],
  "hasAnyCapture": true
}
```

### `CaptureSummaryResponse` — recentCaptures/favorites 공유 DTO

`LLD-0002`에서 스케치했던 것을 실제로 만든다. `recentCaptures`와
`favorites`가 완전히 같은 형태라 공유한다.

```java
public record CaptureSummaryResponse(
        Long captureId, String title, String summary, CardType typeCode,
        String thumbnailUrl, boolean isFavorite, Instant organizedAt
) {
    public static CaptureSummaryResponse from(InfoCard card, String thumbnailUrl) {
        return new CaptureSummaryResponse(
                card.getId(), card.getTitle(), card.getSummary(), card.getType(),
                thumbnailUrl, card.isFavorite(), card.getCreatedAt());
    }
}
```

`thumbnailUrl`은 별도 썸네일 생성 파이프라인이 없으므로,
`InfoCard.originalImageKey`로 발급한 presigned GET URL을 그대로 쓴다
(07-01 상세 화면과 동일한 이미지, 클라이언트가 축소해서 표시).

### 쿼리 설계 — `InfoCardRepository`에 메서드 추가 (신규 리포지토리 안 만듦)

```java
List<InfoCard> findTop3ByUserOrderByCreatedAtDesc(User user); // 30일 필터는 서비스에서
List<InfoCard> findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(User user);
boolean existsByUser(User user);

// 유형별 개수 + 동점 판단(최근 정리 캡처 포함 유형 우선)용 group by
@Query("""
    select c.type as type, count(c) as cnt, max(c.createdAt) as latest
    from InfoCard c
    where c.user = :user and c.type <> cmc.recap.card.domain.CardType.ETC
    group by c.type
    order by count(c) desc, max(c.createdAt) desc
    """)
List<TypeCountProjection> countByTypeExcludingEtc(@Param("user") User user);

// 대표 썸네일용 (선정된 유형별로 최대 4번 호출)
Optional<InfoCard> findFirstByUserAndTypeOrderByCreatedAtDesc(User user, CardType type);
```

**"동수면 최근 정리 캡처 포함 유형 우선"** 규칙은 `order by count(c) desc,
max(c.createdAt) desc`로 한 번에 처리한다. Java에서는 이 결과의 앞 4개만
취한다(CardType이 8개뿐이라 — ETC 제외 — 전체를 가져와도 부담 없음).

**대표 썸네일은 선정된 유형마다 별도 쿼리 1회씩(최대 4회) 조회한다.**
단일 윈도우 함수 쿼리로 합칠 수도 있으나, 이번 규모(유저 1명당 데이터
소량)에서는 단순한 쪽을 택한다.

### 30일 필터

```java
Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
```

서비스 레이어에서 계산해 전달한다(DB 방언에 의존하는 날짜 연산 대신
Java에서 계산 — 이식성).

### `hasAnyCapture`와 `recentCaptures`가 둘 다 비어있을 수 있음

`recentCaptures`가 빈 배열인 것과 `hasAnyCapture=false`는 다른 의미다
(전자는 "30일 이내 캡처 없음", 후자는 "캡처 자체가 0개"). 클라이언트가
이 둘을 조합해 S-02-01(카드 없음)과 "최근 캡처 없음" 유도 문구를 구분해
노출한다(02-01 설계서 정책 그대로).

### 패키지 위치

`Home`은 별도 도메인 엔티티가 아니라 `InfoCard`를 조합해 보여주는
읽기 전용 뷰라, 새 최상위 패키지를 만들지 않고 `cmc.recap.card` 아래
`HomeController`/`HomeApiDocs`/`HomeService`로 둔다.

## 고려한 대안 (Considered Options)

1. **단일 복합 쿼리(윈도우 함수 등)로 전부 한 번에 (기각)** — 구현/유지보수
   복잡도가 커지는 데 비해, 지금 유저당 데이터 규모에서 얻는 성능 이득이
   거의 없음.
2. **topTypes에 ETC 포함 (기각)** — 05-01 보관함 설계서가 유형별 보기에서
   ETC를 명시적으로 제외하고, "홈과 로직 재사용 가능"이라고 명시해 일관성
   유지가 필요.
3. **여러 단순 쿼리로 분리 (채택)** — 위 이유로 지금 규모에 적합.

## 결과 (Consequences)

### 긍정
- `CaptureSummaryResponse` 공유로 recentCaptures/favorites 응답 형태가
  강제로 일관됨.
- 각 쿼리가 단순해 테스트/디버깅이 쉬움.

### 부정 / 트레이드오프
- 홈 화면 로딩 한 번에 쿼리가 최대 8개까지 나갈 수 있음(recentCaptures 1,
  favorites 1, group-by 1, 대표 썸네일 최대 4, hasAnyCapture 1). 유저당
  데이터가 크게 늘어나면(수만 건 단위) 재검토 필요.

## 후속 / 미결정

- [ ] 유저당 캡처 수가 크게 늘어날 경우, group-by+대표 썸네일 조회를
      단일 쿼리(윈도우 함수)로 합칠지 재검토
