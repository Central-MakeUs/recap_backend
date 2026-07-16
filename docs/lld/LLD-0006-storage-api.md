# LLD-0006: 보관함 API (즐겨찾기 · 기타 · 유형별 보기)

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-17 |
| 관련 | [LLD-0004](LLD-0004-home-summary-api.md), [LLD-0005](LLD-0005-capture-detail-favorite-delete.md), 이슈 #16 |

## 맥락 (Context)

05-01(보관함 홈), 05-02(보관함 상세) 화면 기준. 05-01 설계서가 "홈의
자주 저장한 유형과 동일 기준, 개발 로직 재사용 가능"이라고 명시해,
LLD-0004에서 만든 쿼리·DTO를 최대한 재사용하는 방향으로 설계한다.

## 결정 (Decision)

### 응답 DTO

```java
// 목록 3종(즐겨찾기/기타/유형상세) 공유 래퍼 — CaptureSummaryResponse(LLD-0004) 재사용
public record CaptureListResponse(int count, List<CaptureSummaryResponse> items) {
    public static CaptureListResponse of(List<CaptureSummaryResponse> items) {
        return new CaptureListResponse(items.size(), items);
    }
}

// 유형별 보기 전용 — TopTypeResponse(홈)와 다름: 썸네일 대신 대표 제목 최대 2개
public record StorageTypeResponse(CardType typeCode, long count, List<String> representativeTitles) {
    public static StorageTypeResponse of(CardType typeCode, long count, List<String> titles) {
        return new StorageTypeResponse(typeCode, count, titles);
    }
}
```

### 쿼리 — `InfoCardRepository`에 추가 (기존 메서드 재사용 포함)

```java
// 신규
List<InfoCard> findByUserAndFavoriteTrueOrderByFavoritedAtDesc(User user); // 즐겨찾기, 정렬 옵션 없음(고정)
List<InfoCard> findByUserAndType(User user, CardType type, Sort sort);     // 기타/유형상세 공용
List<InfoCard> findTop2ByUserAndTypeOrderByCreatedAtDesc(User user, CardType type); // 대표 제목용

// 재사용 (LLD-0004에서 이미 존재, 변경 없음)
List<TypeCountProjection> countByTypeExcludingEtc(User user, CardType excludedType);
```

`findByUserAndType`는 `Sort` 파라미터로 최신/오래된순을 한 메서드에서
처리한다(정렬 방향마다 메서드를 따로 만들지 않음). `/storage/etc`는
`type=CardType.ETC`로, `/storage/types/{typeCode}/captures`는 선택된
유형으로 같은 메서드를 호출한다 — 두 엔드포인트가 사실상 같은 쿼리다.

`/storage/types`는 `countByTypeExcludingEtc`를 그대로 호출하고
(LLD-0004와 동일 쿼리), 결과 각 유형마다 `findTop2ByUserAndType...`로
대표 제목을 추가 조회한다(유형이 최대 8개이므로 최대 8회 추가 쿼리,
지금 규모에서 문제없음 — LLD-0004와 동일한 판단 근거).

**대표 제목은 최대 30자(`InfoCard.TITLE_MAX_LENGTH`)까지의 전체
문자열을 그대로 반환한다.** 05-01 설계서의 "24자 초과 시 말줄임" 처리는
화면 레이아웃(폰트·너비)에 따라 달라지는 클라이언트 표시 문제이므로
서버가 자르지 않는다.

### API 엔드포인트

| 메서드 | 경로 | 응답 | 비고 |
| --- | --- | --- | --- |
| GET | `/api/v1/storage/favorites` | `CaptureListResponse` | 정렬 옵션 없음(favoritedAt desc 고정) |
| GET | `/api/v1/storage/etc?sort=latest\|oldest` | `CaptureListResponse` | 기본값 `latest` |
| GET | `/api/v1/storage/types` | `List<StorageTypeResponse>` | ETC 제외, 최대 8개 |
| GET | `/api/v1/storage/types/{typeCode}/captures?sort=latest\|oldest` | `CaptureListResponse` | `typeCode=ETC`면 `INVALID_INPUT`(400) |

`sort` 파라미터가 없거나 `latest`/`oldest` 외의 값이면 에러 대신
`latest`로 처리한다(부가적인 UI 취향이라 API를 깨지기 쉽게 만들
이유가 없음).

`GET .../types/{typeCode}/captures`의 `typeCode`가 유효하지 않은
enum 문자열이면(경로 변수 enum 바인딩 실패) `INVALID_INPUT`(400)으로
응답해야 한다 — `GlobalExceptionHandler`에 이 예외
(`MethodArgumentTypeMismatchException` 등)를 처리하는 핸들러가 이미
있는지 구현 시 확인 필요(없으면 이번 이슈에서 추가).

### 패키지 위치

`/storage`는 `/captures`, `/home`과는 다른 최상위 리소스 경로라,
기존 `CaptureController`에 얹지 않고 **`StorageController`/
`StorageApiDocs`/`StorageService`를 신규 생성**한다(`AuthController`,
`CaptureController`, `HomeController`와 같은 급의 독립 컨트롤러).

### 소유권 검증

모든 쿼리가 `user` 파라미터로 이미 범위가 좁혀지므로(다른 유저의
데이터가 애초에 조회되지 않음), 이슈 #13/#15처럼 별도의 "소유권
검증 후 404" 로직이 필요 없다 — `captureId` 단건 조회가 아니라
"내 것만 필터링해서 조회"하는 구조라 이 문제 자체가 발생하지 않는다.

## 고려한 대안 (Considered Options)

1. **`/storage/etc`와 `/storage/types/{typeCode}/captures`를 별도
   메서드로 구현 (기각)** — `ETC`도 `CardType` enum 값이라 쿼리
   구조가 완전히 같음. 중복 코드를 만들 이유가 없음.
2. **`TopTypeResponse`(홈)를 `/storage/types`에서도 재사용 (기각)** —
   홈은 썸네일, 보관함은 대표 제목 최대 2개로 요구사항 자체가 다름.
3. **`typeCode=ETC` 허용 (기각)** — 05-01이 "기타"를 유형별 보기와
   분리된 탭으로 취급, 같은 데이터의 이중 접근 경로를 만들면 일관성이
   깨짐.

## 결과 (Consequences)

### 긍정
- LLD-0004의 쿼리/DTO를 최대한 재사용해 신규 코드가 최소화됨.
- `findByUserAndType` 하나로 두 엔드포인트를 처리해 중복 제거.

### 부정 / 트레이드오프
- `/storage/favorites`, `/storage/etc`가 전체 결과를 페이지네이션 없이
  반환 — 지금 규모(개인용, 유저당 데이터 소량)에서는 문제없으나
  장기적으로 재검토 필요(아래 후속 참고).

## 후속 / 미결정

- [ ] 즐겨찾기/기타 목록이 페이지네이션 없이 무제한 커지는 것에 대한
      재검토 시점 (유저당 데이터가 크게 늘어나면)
- [ ] "전체 캡처 0개"(S-05-01) 판단을 클라이언트가 어떻게 할지 —
      이미 홈 API(`hasAnyCapture`)에서 얻을 수 있는 정보인데, 보관함에
      별도 진입(딥링크 등) 시에도 항상 그 값을 갖고 있다는 보장이
      없어 클라이언트 쪽 처리 방식 확인 필요
