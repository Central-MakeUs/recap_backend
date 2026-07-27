# LLD-0013: 캡처 다중 삭제 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-28 |
| 관련 | [LLD-0005](LLD-0005-capture-detail-favorite-delete.md), [LLD-0009](LLD-0009-user-withdrawal.md), [LLD-0012](LLD-0012-image-expiration-batch.md) |

## 맥락 (Context)

이슈 #16(보관함 API)에서 "편집 모드(다중 선택 삭제)는 이번 스프린트
범위 아님"으로 명시적으로 미뤄뒀던 기능을 구현한다.

`OrganizeService.deleteCards()`(정리 취소), `UserService.withdraw()`
(회원탈퇴)에 이어 S3 배치 삭제 로직이 필요해지는 **세 번째 지점**이다
— LLD-0009가 "세 번째 발생 시 공유 유틸리티 추출 재검토"를 명시적으로
남겨뒀던 그 시점이다. 일정(7/31 제출)을 고려해 이번엔 한 번 더
복제해서 구현하고, 공유 유틸 추출은 제출 이후로 미룬다(아래 후속
참고).

또한 LLD-0012(원본 이미지 자동 만료)로 `InfoCard.originalImageKey`가
`nullable`이 된 것을 반영해야 한다 — 이미 만료된 캡처가 삭제 대상에
섞이면 S3 삭제 대상에서 제외해야 한다.

## 결정 (Decision)

### 소유권 필터링 — 거부 대신 조용히 걸러냄

요청한 `captureId` 중 다른 유저 소유이거나 존재하지 않는 게 섞여
있어도 요청 전체를 거부하지 않는다. 소유한 것만 조회 쿼리 자체에서
자연스럽게 걸러지고, 나머지는 조용히 무시된다(클라이언트가 화면에
보여준 ID만 보낸다는 전제라 실제로는 거의 발생하지 않는 경우).

```java
List<InfoCard> findByIdInAndUser(List<Long> ids, User user);
```

이 쿼리 자체가 "소유한 것만" 반환하므로, 별도의 소유권 검증-후-예외
로직이 필요 없다.

### S3 배치 삭제 — `DeleteObjectsRequest` 패턴 재사용, 1000개 청크 분할

`OrganizeService.deleteCards()`와 동일한 `DeleteObjectsRequest`
패턴을 재사용한다(공유 유틸 추출은 후속으로 미룸, 위 맥락 참고).

**개수 제한을 두지 않기로 했으므로**, S3 `DeleteObjects` API의 1회
최대 1000개 제한을 실제로 지켜야 한다 — 그렇지 않으면 "제한 없음"이
1000개 지점에서 조용히 깨진다. 대상 목록을 1000개 단위로 나눠 여러
번 호출한다.

```java
Lists.partition(targetKeys, 1000).forEach(chunk ->
        s3Client.deleteObjects(...)); // 청크별로 배치 삭제 호출
```

### `originalImageKey`가 `null`인 캡처 처리 (LLD-0012 연동)

LLD-0012로 만료된 캡처는 `originalImageKey`가 `null`일 수 있다.

```
S3 삭제 대상: originalImageKey가 null이 아닌 캡처만
DB 삭제 대상: 조회된 캡처 전체 (null 여부 무관)
```

### API

```
POST /api/v1/captures/bulk-delete
```

**요청**

```json
{ "captureIds": [1, 2, 3] }
```

- `captureIds`가 비어있으면 `INVALID_INPUT`(400) — 아무것도 안 하는
  요청을 조용히 허용하지 않고 명시적으로 거부.
- 최대 개수 제한 없음(위 결정대로).

**응답**: `204 No Content`

기존 `CaptureController`/`CaptureApiDocs`에 추가한다(`/captures`
리소스 하위 연산, 기존 판단 근거와 동일).

### Service 로직

```java
@Transactional
public void bulkDelete(Long userId, List<Long> captureIds) {
    if (captureIds.isEmpty()) {
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    User user = userRepository.getReferenceById(userId);
    List<InfoCard> cards = infoCardRepository.findByIdInAndUser(captureIds, user);

    List<String> imageKeys = cards.stream()
            .map(InfoCard::getOriginalImageKey)
            .filter(Objects::nonNull)
            .toList();
    deleteS3ObjectsInChunks(imageKeys); // 1000개 단위 청크

    infoCardRepository.deleteAll(cards);
}
```

### 에러 코드

| 상황 | ErrorCode | HTTP |
| --- | --- | --- |
| `captureIds`가 빈 배열 | `INVALID_INPUT`(기존 재사용) | 400 |

## 고려한 대안 (Considered Options)

1. **일부 소유권 불일치 시 요청 전체 거부 (기각)** — 정상적인
   클라이언트 사용 흐름에서는 발생할 일이 거의 없는 경우를 위해
   전체 실패 처리하는 건 UX상 손해가 더 큼. 조용히 필터링이 더
   실용적.
2. **지금 바로 S3 배치 삭제 공유 유틸리티 추출 (기각, 지금은)** —
   일정 촉박, 세 번째 중복이지만 제출 후 안전하게 리팩터링하는 쪽을
   택함.
3. **개수 제한 도입 (기각)** — 요구사항대로 제한 없음, 대신 S3 API
   자체 제한(1000개)은 청크 분할로 기술적으로 우회.

## 결과 (Consequences)

### 긍정
- 소유권 필터링을 쿼리 레벨에서 자연스럽게 처리해 별도 검증 로직
  불필요.
- 1000개 청크 분할로 "제한 없음" 요구사항이 실제로 대량 요청에서도
  깨지지 않음.

### 부정 / 트레이드오프
- S3 배치 삭제 로직이 이제 3곳에 중복 존재 — 제출 이후 리팩터링
  필요(아래 후속).

## 후속 / 미결정

- [ ] S3 배치 삭제 로직(`OrganizeService.deleteCards()`,
      `UserService.withdraw()`, 이번 `bulkDelete()`)을 공유
      유틸리티로 추출 — 제출(7/31) 이후 진행
- [ ] 매우 큰 배치 요청(수천 개)에서 DB `deleteAll()` 자체의 성능은
      검증하지 않음, 지금 규모에선 문제없다고 가정
