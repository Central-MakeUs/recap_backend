# LLD-0005: 정보카드 상세조회 · 즐겨찾기 · 삭제 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-17 |
| 관련 | [LLD-0002](LLD-0002-capture-upload-organize-explore.md), [LLD-0004](LLD-0004-home-summary-api.md), 이슈 #15 |

## 맥락 (Context)

07-01(정보카드 상세) 화면 기준. API 명세서에 엔드포인트 구조는 이미
정의되어 있으나(`docs/api-spec/07.13~07.17-스프린트-api-명세서.md` 5번
섹션), 삭제 시 S3/DB 처리 순서와 응답 DTO 구조는 미확정이었다. 이 LLD에서
확정한다.

## 결정 (Decision)

### 응답 DTO — `CaptureSummaryResponse`와 분리

```java
public record CaptureDetailResponse(
        Long captureId, CardType typeCode, String title, String summary,
        String body, String originalImageUrl, boolean isFavorite, Instant organizedAt
) {
    public static CaptureDetailResponse from(InfoCard card, String originalImageUrl) {
        return new CaptureDetailResponse(
                card.getId(), card.getType(), card.getTitle(), card.getSummary(),
                card.getBody(), originalImageUrl, card.isFavorite(), card.getCreatedAt());
    }
}
```

`extractedText`는 검색 전용이라 어떤 응답에도 노출하지 않는다(기존 원칙).

### 즐겨찾기 요청 DTO

```java
public record FavoriteRequest(boolean isFavorite) {}
```

`InfoCard.markFavorite(boolean)`(이슈 ①에서 이미 구현됨, 멱등적)을
그대로 사용한다.

### 소유권 검증

`captureId`로 조회한 `InfoCard`의 `user`가 요청자와 다르면, 존재 여부를
숨기기 위해 `ErrorCode.NOT_FOUND`(404)로 응답한다. 이슈 #13의
`OrganizeService.validateOwner()`와 동일한 패턴. 신규 ErrorCode는
필요 없다.

### 삭제 순서 — S3 먼저, DB 나중

```
1. InfoCard 조회 + 소유권 검증
2. S3Client.deleteObject(originalImageKey)
3. infoCardRepository.delete(card)
```

이슈 #13(`OrganizeService.deleteCards()`)에서 이미 확립한 순서를
재사용한다. S3 삭제 성공 후 DB 삭제가 실패해도, 남은 `InfoCard`는
07-01의 "원본 이미지 로딩 실패"(S-07-01) 상태로 안전하게 처리된다 —
반대 순서(DB 먼저)는 S3 고아 파일이 조용히 영구적으로 남는 더 나쁜
실패 모드다.

배치 삭제(`DeleteObjectsRequest`)가 아니라 단건 삭제
(`DeleteObjectRequest`)를 쓴다 — 한 번에 이미지 1개만 지우므로.

`OrganizeBatch`의 `successCount` 등은 건드리지 않는다 — 배치 카운트는
"그 배치 실행 시점에 몇 개가 성공했는가"라는 과거 사실의 기록이라,
이후 개별 캡처가 삭제돼도 소급 변경하지 않는다.

### 즐겨찾기·삭제는 소프트 삭제 인프라 없이 진행

`deletedAt` 같은 컬럼을 지금 미리 만들지 않는다. 이슈 본문이 "우선
하드 삭제, 필요 시 후속 전환"이라고 명시했고, 아직 결정 안 된 기능을
위한 스키마를 미리 만드는 건 YAGNI 위반이다. 전환 시점에 별도 LLD로
다룬다.

### API 엔드포인트

| 메서드 | 경로 | 응답 |
| --- | --- | --- |
| GET | `/api/v1/captures/{captureId}` | 200, `CaptureDetailResponse` |
| PATCH | `/api/v1/captures/{captureId}/favorite` | 204 |
| DELETE | `/api/v1/captures/{captureId}` | 204 |

기존 `CaptureController`/`CaptureApiDocs`(이슈 #13에서 생성됨)에 추가한다
— 별도 컨트롤러를 새로 만들지 않는다(같은 `/captures` 리소스의 하위
연산). 서비스 로직도 기존 `CaptureService`(이슈 #13, 현재
`issueUploadUrls`만 있음)에 추가한다 — `OrganizeService`는 배치/비동기
정리 흐름 전담으로 역할을 유지한다.

## 고려한 대안 (Considered Options)

1. **DB 먼저 삭제, S3 나중 (기각)** — S3 삭제 실패 시 고아 파일이
   영구적으로 조용히 남는 위험이 더 큼.
2. **`CaptureSummaryResponse` 재사용 (기각)** — `body` 필드 유무가
   달라 목록/상세 응답의 관심사가 다름. 억지로 합치면 목록 API가
   불필요한 필드를 짊어짐.
3. **소프트 삭제 컬럼 선제 추가 (기각)** — 결정 안 된 기능을 위한
   스키마 선점, YAGNI 위반.

## 결과 (Consequences)

### 긍정
- 이슈 #13의 검증된 패턴(소유권 검증, S3-먼저-삭제 순서)을 그대로
  재사용해 새로운 리스크가 적음.
- 상세/목록 DTO 분리로 각 API의 응답이 최소한으로 유지됨.

### 부정 / 트레이드오프
- S3 삭제와 DB 삭제 사이에 완전한 원자성은 없음(외부 시스템이라
  트랜잭션 롤백 불가) — "더 안전한 실패 모드"를 택했을 뿐, 실패
  자체를 없앤 건 아님.

## 후속 / 미결정

- [ ] 소프트 삭제 전환 시점 및 방식(별도 LLD)
- [ ] S3 삭제 실패가 반복되는 경우의 모니터링/알림 필요 여부
