# LLD-0002: 스크린샷 업로드 · 정리 · 탐색 플로우

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-13 |
| 관련 | [ADR-0010](../adr/ADR-0010-original-image-s3-storage.md), [ADR-0011](../adr/ADR-0011-infocard-flat-summary-fields.md), [ADR-0012](../adr/ADR-0012-presigned-url-image-upload.md), `docs/api-spec/07.13~07.17-스프린트-api-명세서.md` |

## 맥락 (Context)

이번 스프린트는 화면 설계서(02 홈, 03 정리하기, 05 보관함, 06 검색,
07 정보카드) 기준으로 "핵심 플로우 3개"(업로드/정리/탐색)를 구현한다.
API 계약은 `docs/api-spec/07.13~07.17-스프린트-api-명세서.md`에 이미
확정되어 있고, 이 LLD는 그 계약을 실제로 구현하기 위한 엔티티·서비스
설계를 다룬다.

## 결정 (Decision)

### 엔티티 변경

**InfoCard (기존 엔티티 수정)**

```java
@Entity
@Table(name = "info_cards")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InfoCard extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CardType type;

    @Column(name = "title", nullable = false, length = 30)
    private String title;

    @Column(name = "summary", length = 80)
    private String summary;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "original_image_key", nullable = false, length = 255)
    private String originalImageKey;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText; // OCR 원문, 검색 전용, 응답 미노출

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @Column(name = "favorited_at")
    private Instant favoritedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organize_batch_id")
    private OrganizeBatch batch;

    public static InfoCard create(User user, CardType type, String title,
                                   String summary, String body,
                                   String originalImageKey, String extractedText,
                                   OrganizeBatch batch) {
        validateTitle(title);
        InfoCard card = new InfoCard();
        card.user = user;
        card.type = type;
        card.title = title;
        card.summary = summary;
        card.body = body;
        card.originalImageKey = originalImageKey;
        card.extractedText = extractedText;
        card.batch = batch;
        card.favorite = false;
        return card;
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제목은 필수입니다.");
        }
        if (title.length() > 30) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제목은 30자를 초과할 수 없습니다.");
        }
    }

    /** 명시적 값으로 즐겨찾기 상태를 설정한다. 멱등적이라 재시도에 안전하다. */
    public void markFavorite(boolean isFavorite) {
        this.favorite = isFavorite;
        this.favoritedAt = isFavorite ? Instant.now() : null;
    }
}
```

domain-design-principles.md 원칙대로 `title` 검증을 생성자에서 강제하고,
`markFavorite(boolean)`은 기존 `toggleFavorite()`를 대체한다(비멱등 문제
해결, ADR 확인 사항 4번).

**`CardSummary`/`CardSummaryConverter`는 삭제하지 않고 그대로 둔다**
(ADR-0011). `InfoCard`에서 더 이상 참조하지 않을 뿐이다.

**`keywords` 필드**: 검색이 title/summary/body/extractedText를 직접
대상으로 하므로 이번 스프린트에는 사용하지 않는다. 필드 자체를 지울지는
후속 미결정(아래 참고) — 지금은 그대로 두되 신규 생성 캡처에 채우지
않는다.

**OrganizeBatch (신규 엔티티)**

```java
@Entity
@Table(name = "organize_batches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeBatch extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BatchStatus status;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    public static OrganizeBatch start(User user, int totalCount) {
        OrganizeBatch batch = new OrganizeBatch();
        batch.user = user;
        batch.status = BatchStatus.PROCESSING;
        batch.totalCount = totalCount;
        batch.successCount = 0;
        batch.failCount = 0;
        batch.acknowledged = false;
        return batch;
    }

    /** 이미지 1건 처리 완료 시 호출. 개별 커밋 아키텍처의 핵심 메서드. */
    public void recordSuccess() {
        this.successCount++;
        refreshStatusIfDone();
    }

    public void recordFailure() {
        this.failCount++;
        refreshStatusIfDone();
    }

    public void cancel() {
        if (this.status == BatchStatus.PROCESSING) {
            this.status = BatchStatus.CANCELLED;
        }
    }

    public void acknowledge() {
        this.acknowledged = true;
    }

    private void refreshStatusIfDone() {
        if (this.status == BatchStatus.CANCELLED) {
            return; // 취소된 배치는 상태를 되돌리지 않음
        }
        if (successCount + failCount < totalCount) {
            return; // 아직 처리 중
        }
        if (failCount == 0) {
            this.status = BatchStatus.COMPLETED;
        } else if (successCount == 0) {
            this.status = BatchStatus.FAILED;
        } else {
            this.status = BatchStatus.PARTIAL_FAILED;
        }
    }
}
```

```java
public enum BatchStatus {
    PROCESSING, COMPLETED, PARTIAL_FAILED, FAILED, CANCELLED
}
```

### 이미지 업로드 (ADR-0012)

```java
public interface ImagePresignedUrlProvider {
    PresignedUploadInfo issueUploadUrl(String objectKey);
    URL issueDownloadUrl(String objectKey); // 07-01 원본 이미지 조회용
}

public record PresignedUploadInfo(String uploadUrl, Instant expiresAt) {}
```

`ObjectKey` 규칙: `captures/{userId}/{uuid}.jpg` — 사용자별 경로 분리로
S3 IAM 정책을 사용자 단위로 좁힐 여지를 남긴다(지금 당장은 애플리케이션
레벨에서만 소유권 검증).

### 정리(Organize) 처리 — 개별 커밋 아키텍처

`POST /captures/organize`는 `OrganizeBatch`를 `PROCESSING`으로 생성하고
즉시 응답한 뒤, 각 이미지를 **비동기로 개별 처리**한다.

AI 분석은 [ADR-0013](../adr/ADR-0013-image-analysis-pipeline.md)에서
확정한 `ImageAnalysisProvider` 인터페이스를 통해 수행한다.

```java
public interface ImageAnalysisProvider {
    ImageAnalysisResult analyze(String imageKey);
}

public record ImageAnalysisResult(
        CardType type, String title, String summary, String body, String extractedText
) {}
```

```
이미지 1건마다:
  1. S3에서 이미지 로드 (또는 이미지 참조로 AI 분석 API 호출)
  2. ImageAnalysisProvider.analyze(imageKey) 호출 → CardType, title,
     summary, body, extractedText 추출
  3. InfoCard.create(...) → 즉시 저장 (커밋)
  4. batch.recordSuccess() 또는 batch.recordFailure()

→ 하나가 실패해도 나머지에 영향 없음. 취소돼도 이미 커밋된 InfoCard는
  유지됨(확인 사항 7번, 임시로 이 방식 채택 — PM/팀 재확인 필요).
```

🟡 **구현 방식 미결정**: 비동기 처리를 `@Async` + `@Transactional`
조합으로 할지, 별도 큐(향후 SQS 등)를 쓸지는 이번 스프린트는 `@Async`로
단순하게 시작하는 것을 제안한다 — 현재 인프라(단일 EC2 인스턴스)
규모에서 큐 도입은 과할 수 있음(YAGNI).

### API 엔드포인트

`docs/api-spec/07.13~07.17-스프린트-api-명세서.md`에 확정되어 있으므로 이
LLD에서 반복하지 않는다. 요약만 남긴다.

| 그룹 | 엔드포인트 |
| --- | --- |
| 업로드/정리 | `POST /captures/upload-urls`, `POST /captures/organize`, `GET /captures/organize/{batchId}/status`, `POST /captures/organize/{batchId}/cancel`, `GET /captures/organize/pending-result`, `POST /captures/organize/{batchId}/ack` |
| 홈 | `GET /home/summary` |
| 보관함 | `GET /storage/favorites`, `GET /storage/etc`, `GET /storage/types`, `GET /storage/types/{typeCode}/captures` |
| 정보카드 | `GET /captures/{captureId}`, `PATCH /captures/{captureId}/favorite`, `DELETE /captures/{captureId}` |
| 검색 | `GET /search` |

### 응답 DTO 생성 규칙

`docs/conventions/domain-design-principles.md` #5(DTO 생성 규칙)를
따라 `from(entity)` / `of(값...)` 정적 팩토리를 사용한다.

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

## 고려한 대안 (Considered Options)

API 명세서 검수 과정에서 이미 대안을 검토했으므로 여기서는 반복하지
않는다. ADR-0010/0011/0012 참고.

## 결과 (Consequences)

### 긍정
- API 계약(명세서)과 엔티티 설계가 사전에 합의된 상태에서 구현을
  시작할 수 있음.
- `OrganizeBatch`의 개별 커밋 방식이 부분 실패를 자연스럽게 처리.

### 부정 / 트레이드오프
- `@Async` 기반 비동기 처리는 애플리케이션 재시작 시 진행 중이던
  작업이 유실될 수 있음(인메모리 스레드풀 한계) — 트래픽이 커지면
  재검토 필요.
- `CardSummary` 관련 코드가 당분간 미사용 상태로 남아, 코드베이스를
  처음 보는 사람에게 혼란을 줄 수 있음(주석/문서로 보완).

## 후속 / 미결정

- [ ] 정리 취소 시 이미 완료된 항목 처리 — 위 "개별 커밋" 방식을
      최종 확정할지 재확인
- [ ] 정리 실패 상세 케이스(AI 분석 실패 외 추가 케이스) 리스트업
- [ ] S3 고아 파일 정리 배치 (ADR-0012 후속)
- [ ] `keywords` 필드 완전 제거 여부
- [ ] `@Async` 처리 방식의 장애 허용 수준 — 트래픽 증가 시 재검토
