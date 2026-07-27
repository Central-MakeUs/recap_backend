# LLD-0012: 원본 이미지 자동 만료 삭제 배치

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted (개정) |
| 날짜 | 2026-07-28, 1차 개정 2026-07-28 |
| 관련 | [ADR-0010](../adr/ADR-0010-original-image-s3-storage.md), [LLD-0005](LLD-0005-capture-detail-favorite-delete.md) |

## 개정 이력

- 2026-07-28: `originalImageKey` 참조 지점을 실제로 전수 조사한 결과,
  최초 문서가 "`CaptureService.getDetail()` 등은 분석 시점엔 항상
  값이 있어 영향 없음"이라고 적었던 게 틀렸음이 확인됨(`getDetail()`은
  분석 시점이 아니라 상세 화면 진입 시 언제든 호출됨). 이 메서드
  포함 총 6곳에 null 분기가 필요한 것으로 재확인되어 "결정" 섹션에
  전체 목록을 명시적으로 추가.

## 맥락 (Context)

ADR-0010이 "원본 이미지 보관 1개월"을 임시값으로 남기고, 실제로
기간이 지난 이미지를 정리하는 배치 자체를 후속 미결정으로 남겨뒀다.
설계 중, "이미지를 지우면 07-01 상세 화면에서 이미지를 못 보는데,
정보카드는 계속 볼 수 있어야 한다"는 요구사항과의 모순을 발견했다.

**정보카드(텍스트)는 영구 보존하고 원본 이미지만 1개월 후 삭제**하는
것으로 확정한다. 이 시나리오는 07-01 설계서에 이미 존재한다 —
"원본 이미지 로딩 실패는 캡처 삭제나 오류 처리로 이어지지 않으며,
유형·제목·요약·본문은 정상 노출한다"는 문구가 정확히 이 상태를
전제로 하고 있었다(원래는 일시적 네트워크 오류를 위한 설계였으나,
영구 만료 상황에도 그대로 재사용 가능).

## 결정 (Decision)

### 보관 기간 최종 확정 — 1개월

ADR-0010의 "PM 논의 필요" 후속 항목을 해소한다. 1개월을 최종값으로
확정한다.

### 삭제 방식 — 기존 패턴과 다름: DB row는 유지, 필드만 비움

```
기존 삭제 패턴(정리 취소, 단건 삭제, 회원탈퇴)
  S3 오브젝트 삭제 → InfoCard row 자체를 DB에서 삭제

이번 배치
  S3 오브젝트 삭제 → InfoCard row는 그대로 두고
                    originalImageKey 컬럼만 null로 변경
```

`InfoCard.originalImageKey`를 `nullable`로 변경한다(기존 `not null`).

```java
public void expireOriginalImage() {
    this.originalImageKey = null;
}
```

### `originalImageKey`를 참조하는 모든 지점에 null 분기 필요 (전수 조사 확정)

`card.getOriginalImageKey()`를 호출하는 지점을 grep으로 전수
조사했다. 총 8곳 중 2곳은 원래 안전(항상 값이 있는 시점에만 호출),
6곳은 null 분기가 필요하다.

**안전 — 수정 불필요**

| 위치 | 이유 |
| --- | --- |
| `GeminiImageAnalysisProvider` | `InfoCard`를 참조하지 않음, 업로드 직후 원시 S3 키를 파라미터로 받아 분석하는 것뿐 |
| `OrganizeService.deleteCards()` | 정리 취소 시 방금 생성된 배치의 카드만 대상 — 이미지가 만료될 시간이 없음 |

**null 분기 필요 — 이번 이슈에서 함께 수정**

| 위치 | 상황 | 처리 방식 |
| --- | --- | --- |
| `CaptureService.getDetail()` | 상세조회 | `null`이면 presigned URL 발급 시도 안 함, `originalImageUrl: null` 응답 |
| `CaptureService.delete()` | 단건 삭제 | `null`이면 S3 삭제 단계를 건너뛰고 `InfoCard` row 삭제만 진행 |
| `HomeService.issueThumbnailUrl()` | 홈 요약(즐겨찾기·최근) | `null`이면 presigned 발급 호출 없이 즉시 `null` 반환 |
| `SearchService.issueThumbnailUrl()` | 검색 결과 썸네일 | 동일 |
| `StorageService.issueThumbnailUrl()` | 보관함(즐겨찾기/유형별) | 동일 |
| `UserService.withdraw()` | 회원탈퇴 시 일괄 S3 삭제 | `null`인 카드는 S3 삭제 대상에서 제외, `InfoCard` row 삭제는 정상 진행 |

`Home`/`Search`/`Storage` 3곳의 `issueThumbnailUrl()` 헬퍼가 사실상
동일 로직으로 중복되는 것은 알려진 사실이나, 이번엔 각각 개별
수정한다(공유 유틸 추출은 지금 하지 않음 — 일정 압박 고려, 후속
참고).

### 클라이언트가 "일시적 실패"와 "영구 만료"를 구분할 수 있어야 함

`CaptureDetailResponse.originalImageUrl`은 Java `String`이라 이미
nullable이므로 타입 변경은 불필요, nullable 의미를 설명하는 주석만
추가한다. 서비스 레이어에서 `card.getOriginalImageKey()`가 `null`이면
presigned URL 발급 자체를 시도하지 않고 `originalImageUrl: null`을
그대로 응답한다.

```
originalImageUrl이 null                → 영구 만료(재시도 무의미,
                                          클라이언트가 "원본 이미지는
                                          1개월 후 삭제됩니다" 같은
                                          안내로 전환 가능)
originalImageUrl에 값이 있는데 로딩 실패 → 일시적 오류(기존
                                          S-07-01 재시도 동작 유지)
```

### 배치 설계

```java
@Component
public class OriginalImageExpirationScheduler {

    private static final Period RETENTION_PERIOD = Period.ofMonths(1);

    @Scheduled(cron = "0 0 4 * * *") // 매일 04:00 (트래픽 낮은 시간대)
    public void expireOldImages() {
        Instant cutoff = Instant.now().minus(RETENTION_PERIOD... ); // 구현 시 Period→Instant 변환 방식 확인 필요
        List<InfoCard> targets = infoCardRepository
                .findByCreatedAtBeforeAndOriginalImageKeyIsNotNull(cutoff);
        for (InfoCard card : targets) {
            try {
                s3Client.deleteObject(...); // 단건 삭제, CaptureService.delete()와 동일 패턴
                card.expireOriginalImage();
                infoCardRepository.save(card);
            } catch (Exception e) {
                log.error("원본 이미지 만료 삭제 실패: captureId={}", card.getId(), e);
                // 이 카드는 건드리지 않고 다음으로 — 다음날 배치가
                // originalImageKey IS NOT NULL 조건으로 자동 재시도
            }
        }
    }
}
```

**개별 처리(전체 배치 트랜잭션 하나로 묶지 않음)**: 한 건 실패해도
나머지에 영향 없어야 한다(정리 파이프라인의 "개별 커밋" 철학과
동일). 실패한 건은 `originalImageKey`가 그대로 남아있으므로, 다음날
배치의 조회 조건(`originalImageKey IS NOT NULL`)에 자연스럽게 다시
걸려 재시도된다 — 별도 재시도 로직을 만들지 않아도 자동으로
자가 치유된다.

**배치 삭제(`DeleteObjectsRequest`)가 아니라 단건 삭제를 쓰는 이유**:
하루 한 번, 트래픽 낮은 시간대에 도는 작업이라 S3 API 호출 효율보다
"한 건씩 독립적으로 성공/실패 판단"이 더 중요하다고 판단. 배치
API는 부분 실패 시 응답을 파싱해 개별 성공/실패를 가려내야 해서
복잡도가 늘어난다.

### 삭제 대상 판단 기준

`InfoCard.createdAt`(정리 완료 시점, 기존 관례 그대로) 기준으로
통일한다.

### 신규 Repository 메서드

```java
List<InfoCard> findByCreatedAtBeforeAndOriginalImageKeyIsNotNull(Instant cutoff);
```

### `@EnableScheduling` 필요

프로젝트에 아직 스케줄러 설정이 없다(`@EnableAsync`는 있음). 별도
`SchedulingConfig` 또는 기존 설정 클래스에 `@EnableScheduling` 추가
필요.

## 고려한 대안 (Considered Options)

1. **보관 기간 연장 (기각)** — ADR-0010이 원래 해결하려던 스토리지
   비용 문제를 다시 키움.
2. **정보카드까지 1개월 후 함께 삭제 (기각)** — RECAP의 핵심 가치
   ("다시 찾아볼 수 있게 정리")를 정면으로 훼손. 장기 참고용
   정보(채용 마감일이 먼 공고, 언젠가 가고 싶은 장소 등)까지 조기
   소실됨.
3. **이미지만 만료, 정보카드 영구 보존 (채택)** — 07-01 설계서에
   이미 "이미지 없어도 텍스트는 정상 노출"이라는 상태가 존재해
   추가 UI 설계 비용이 낮음. 사용자의 주 사용 패턴(검색, 목록 조회,
   본문 읽기)이 전부 텍스트 기반이라 실질적 영향이 작음.

## 결과 (Consequences)

### 긍정
- ADR-0010의 미해결 항목(자동 삭제 배치)이 해소됨.
- 스토리지 비용 절감(핵심 목적)과 서비스 가치(정보 영구 접근) 둘 다
  달성.
- 실패 시 자동 재시도가 별도 로직 없이 조회 조건만으로 자연스럽게
  이뤄짐.

### 부정 / 트레이드오프
- `InfoCard.originalImageKey`가 `nullable`이 되면서, 이 필드를
  참조하는 6개 지점(위 표 참고)에 전부 null 분기를 추가해야 하는
  변경 범위가 예상보다 넓어짐.
- 클라이언트가 "일시적 실패"와 "영구 만료" 두 상태를 구분하는 UI
  분기를 새로 만들어야 함(서버는 값만 제공, UI 문구는 클라이언트
  책임).

## 후속 / 미결정

- [ ] S3 `DeleteObjects` API의 1회 최대 1000개 제한 — 지금 규모에선
  문제없으나, 데이터가 크게 늘어나면 페이지네이션/청크 처리 필요
- [ ] 만료 안내 UI 문구는 클라이언트팀과 별도 협의 필요
- [ ] `Home`/`Search`/`Storage`의 `issueThumbnailUrl()` 중복(3곳
  동일 로직)을 공유 유틸리티로 추출할지 — 지금은 일정상 개별
  수정, 네 번째 유사 사례가 생기면 재검토(다중 삭제 API 등에서
  이미 논의된 "세 번째 발생 시 추출" 기준과 별개로, 이건 이미
  3곳이라 근접함)