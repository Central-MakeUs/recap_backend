# LLD-0007: 검색 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-18 |
| 관련 | [LLD-0004](LLD-0004-home-summary-api.md), [LLD-0006](LLD-0006-storage-api.md), 이슈 #17 |

## 맥락 (Context)

06-01(검색), 06-02(검색 결과) 화면 기준. 이슈 자체가 이번 스프린트에서
가장 구현 난이도가 높다고 명시했다. 추가로, 최초 API 명세서 초안에서
"추후 고도화"로 미뤄뒀던 **본문/추출텍스트 전용 매칭 시 발췌 노출**이
PM 검토 결과 이번 스프린트로 앞당겨졌다(팀 논의 결과 반영, 아래
"결정" 참고).

## 결정 (Decision)

### API

```
GET /api/v1/search?q={검색어}&scope=all|favorite|etc|type&typeCode={선택}&page=0&size=20
```

- `q`: 서비스 레이어에서 trim + 중간 연속 공백을 1칸으로 정규화 후
  1~100자 검증. 공백만 남으면(빈 문자열이 되면) `INVALID_INPUT`.
- `scope=type`일 때 `typeCode` 필수, 없으면 `INVALID_INPUT`.
- 대소문자 무시 매칭(SQL `lower()` 명시 사용 — DB collation에
  의존하지 않기 위함).

### 매칭·랭킹 쿼리 — 단일 쿼리 + 조건부 바인드 파라미터

`scope`별로 쿼리를 4개 만들지 않는다. 하나의 `@Query`에 `favoriteOnly`,
`filterType`을 조건부 바인드 파라미터로 넘겨 `scope`를 서비스 레이어에서
매핑한다.

```java
@Query("""
    select c from InfoCard c
    where c.user = :user
      and (lower(c.title) like lower(concat('%', :q, '%'))
        or lower(c.summary) like lower(concat('%', :q, '%'))
        or lower(c.body) like lower(concat('%', :q, '%'))
        or lower(c.extractedText) like lower(concat('%', :q, '%')))
      and (:favoriteOnly = false or c.favorite = true)
      and (:filterType is null or c.type = :filterType)
    order by
      case
        when lower(c.title) like lower(concat('%', :q, '%')) then 1
        when lower(c.summary) like lower(concat('%', :q, '%')) then 2
        when lower(c.body) like lower(concat('%', :q, '%')) then 3
        else 4
      end,
      c.createdAt desc
    """)
Page<InfoCard> search(@Param("user") User user, @Param("q") String q,
                       @Param("favoriteOnly") boolean favoriteOnly,
                       @Param("filterType") CardType filterType,
                       Pageable pageable);
```

`scope` → 파라미터 매핑 (서비스 레이어):

```
all      → favoriteOnly=false, filterType=null
favorite → favoriteOnly=true,  filterType=null
etc      → favoriteOnly=false, filterType=CardType.ETC
type     → favoriteOnly=false, filterType={요청의 typeCode}
```

🟡 **확인 필요(구현 시)**: `order by`에 `CASE`가 들어간 JPQL을 Spring
Data가 페이지네이션 COUNT 쿼리로 자동 변환할 때 문제없이 동작하는지
실제로 검증 필요 — 안 되면 `countQuery` 속성을 `@Query`에 명시적으로
추가한다.

### 하이라이트 — 첫 매칭 지점 1곳만

제목·요약은 검색어가 매칭되면 `<mark>...</mark>`로 감싼다. **여러 곳에
매칭돼도 첫 번째 지점만 감싼다**(제목 30자, 요약 80자로 짧아 실효성이
낮고, 지금 구현 복잡도를 늘릴 이유가 없음 — YAGNI). 대소문자 무시
매칭이지만, 감싸는 결과는 원문 대소문자를 그대로 유지한다.

순수 문자열 로직이라 `SearchHighlighter`(정적 유틸 클래스, DB/Spring
의존 없음)로 분리한다 — `SearchService`는 오케스트레이션만 담당.

```java
public final class SearchHighlighter {
    public static String highlight(String text, String query) { ... }
    public static String excerptOcrMatch(String extractedText, String query) { ... }
}
```

### 본문/추출텍스트 전용 매칭 시 발췌 노출 (신규 확정)

**적용 조건**: title/summary/body 어디에도 매칭이 없고 `extractedText`
에서만 매칭된 경우에만 적용한다. 랭킹 쿼리의 우선순위 판정("가장 높은
우선순위 1개 기준")과 동일한 기준을 재사용한다 — 별도 판단 로직을
새로 만들지 않는다.

```
발췌 길이: 매칭 지점 기준 앞뒤 각 20자 (SEARCH_EXCERPT_PADDING 상수)
자르기 방식: 코드 포인트 단위 (domain-design-principles.md #7 재적용,
            substring(0, N) 같은 UTF-16 code unit 기준 자르기 금지)
잘렸으면 앞/뒤에 "…" 부착
하이라이트: 발췌 안에서 매칭 구간만 <mark> 감쌈
라벨: 서버는 "OCR 추출 내용" 같은 문구를 내려주지 않는다. 응답 필드
      이름(ocrExcerptHighlighted)만으로 구분하고, 실제 라벨 문구는
      클라이언트가 UI 텍스트로 붙인다(24자 말줄임 처리를 클라이언트
      책임으로 남겼던 것과 같은 논리 — 서버는 문구 대신 여지만 준다).
```

**격리 설계(중요)**: 이 기능은 `SearchHighlighter.excerptOcrMatch()`
메서드 하나 + `SearchResultResponse.ocrExcerptHighlighted`(nullable)
필드 하나로만 존재한다. 랭킹 쿼리, 제목/요약 하이라이트 로직에는
관여하지 않는다. **일정상 이 기능만 잘라내야 하면, 이 메서드 호출
한 줄과 필드 하나만 제거하면 되고 나머지 검색 기능은 영향 없다.**

### 응답 DTO

```java
public record SearchResultResponse(
        Long captureId, CardType typeCode, String thumbnailUrl,
        String titleHighlighted, String summaryHighlighted,
        String ocrExcerptHighlighted, // nullable — title/summary/body 매칭 시 null
        boolean isFavorite, Instant organizedAt
) {
    public static SearchResultResponse of(InfoCard card, String thumbnailUrl,
            String titleHighlighted, String summaryHighlighted, String ocrExcerptHighlighted) {
        return new SearchResultResponse(card.getId(), card.getType(), thumbnailUrl,
                titleHighlighted, summaryHighlighted, ocrExcerptHighlighted,
                card.isFavorite(), card.getCreatedAt());
    }
}

public record SearchResponse(long count, boolean hasNext, List<SearchResultResponse> items) {
    public static SearchResponse of(Page<SearchResultResponse> page) {
        return new SearchResponse(page.getTotalElements(), page.hasNext(), page.getContent());
    }
}
```

`body` 필드는 응답에 없다 — 06-02 설계서의 결과 리스트가 본문을
표시하지 않는다(썸네일/제목/요약/유형/정리일/즐겨찾기만). 본문은
매칭 대상일 뿐 노출 대상이 아니다.

### 패키지 위치

`/search`는 독립 리소스 경로라 `SearchController`/`SearchApiDocs`/
`SearchService`를 신규 생성한다(`HomeController`, `StorageController`와
같은 급).

## 고려한 대안 (Considered Options)

1. **scope별 쿼리 메서드 4개 분리 (기각)** — 매칭·랭킹 로직이 4곳에
   중복됨. 조건부 바인드 파라미터 하나로 통합 가능해 불필요.
2. **발췌 기능을 `SearchService` 안에 바로 구현 (기각)** — 나중에
   잘라내야 할 가능성이 있는 기능을 오케스트레이션 로직과 섞으면
   되돌리기 비용이 커짐. 별도 유틸로 격리.
3. **매칭 전체 지점 하이라이트 (기각)** — 제목/요약이 짧아 실효성
   낮고 구현 복잡도만 늘어남.

## 결과 (Consequences)

### 긍정
- 조건부 파라미터 방식으로 쿼리 중복 없이 4가지 scope 처리.
- 발췌 기능이 격리돼 있어 일정 압박 시 부담 없이 축소 가능.

### 부정 / 트레이드오프
- MySQL `LIKE` 기반이라 대량 데이터에서는 성능이 떨어질 수 있음
  (지금 유저당 데이터 규모에서는 문제없음, FULLTEXT 인덱스나
  검색 엔진 도입은 후속 과제).
- `order by CASE`가 포함된 쿼리라 인덱스 활용이 제한적 — 지금 규모에선
  무시할 수준.

## 후속 / 미결정

- [ ] 발췌 길이(20자)는 임시값, 디자인 확정 후 조정 가능
- [ ] 데이터가 크게 늘어나면 `LIKE` 기반 검색을 FULLTEXT 인덱스 또는
      전용 검색 엔진으로 전환할지 재검토
