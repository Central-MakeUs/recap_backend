# LLD-0011: 정보카드 본문(body) 수정 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Superseded by [LLD-0017](LLD-0017-capture-full-update.md) |
| 날짜 | 2026-07-28 |
| 관련 | [LLD-0005](LLD-0005-capture-detail-favorite-delete.md), [LLD-0008](LLD-0008-body-type-templates.md) |

> **대체됨**: 범위가 본문(body)만에서 제목/요약/본문/유형 4개
> 필드로 확장되면서(fix#48), 이 문서의 전제(본문만 수정) 자체가
> 뒤집혔다. 부분 개정이 아니라 [LLD-0017](LLD-0017-capture-full-update.md)로
> 새로 작성. `PATCH /captures/{captureId}/body`는 제거되고
> `PATCH /captures/{captureId}`로 대체됨.

## 맥락 (Context)

AI 분석 결과가 부정확하거나 사용자가 정보를 추가하고 싶을 때, 본문을
직접 수정할 수 있어야 한다. 초기 07-02(정보카드 수정) 설계는 제목·
유형·요약·본문 전체 수정을 다뤘으나, 이번엔 **body만**으로 범위를
명시적으로 좁힌다.

## 결정 (Decision)

### `BODY_MAX_LENGTH`를 `InfoCard`로 이동 (단일 진실 공급원화)

```java
// InfoCard.java
public static final int TITLE_MAX_LENGTH = 30;   // 기존
public static final int SUMMARY_MAX_LENGTH = 80; // 기존
public static final int BODY_MAX_LENGTH = 1000;  // 신규 이동
```

기존 `GeminiImageAnalysisProvider`(또는 `CardAnalysisPrompt`)에 있던
`BODY_MAX_LENGTH` 상수는 삭제하고, `InfoCard.BODY_MAX_LENGTH`를
참조하도록 변경한다. AI 생성 시 프롬프트에 넣는 값과, 사용자가 직접
수정할 때 검증하는 값이 서로 다른 상수로 갈라져 있으면 나중에 하나만
바뀌고 다른 하나는 안 바뀌는 불일치가 생길 수 있다 — 지금 미리
합쳐둔다.

### `InfoCard` 엔티티 변경

```java
@Column(name = "body_edited", nullable = false)
private boolean bodyEdited; // 기본값 false

@Column(name = "body_edited_at")
private Instant bodyEditedAt; // nullable

public void updateBody(String newBody) {
    validateBody(newBody);
    this.body = newBody;
    this.bodyEdited = true;
    this.bodyEditedAt = Instant.now();
}

private static void validateBody(String body) {
    if (body != null && body.length() > BODY_MAX_LENGTH) {
        throw new BusinessException(ErrorCode.INVALID_INPUT,
                "본문은 " + BODY_MAX_LENGTH + "자를 초과할 수 없습니다.");
    }
}
```

`BaseTimeEntity.updatedAt`을 재사용하지 않는다 — 그 필드는 즐겨찾기
토글 등 다른 변경에도 함께 갱신되어 "body가 정말 수정됐는지"를
부정확하게 나타낸다. 별도 `bodyEditedAt`으로 분리한다.

`body`는 `null`/공백도 허용한다(title과 달리 nullable, 사용자가
지우고 싶을 수도 있음) — 길이 초과만 막는다.

### API

```
PATCH /api/v1/captures/{captureId}/body
```

**요청**

```json
{ "body": "수정할 본문 내용" }
```

**응답**: `204 No Content`

기존 `CaptureController`/`CaptureApiDocs`에 추가한다(같은 `/captures`
리소스 하위 연산, 이슈 #15/이슈 신고 기능과 동일 판단).

### 소유권 검증

기존 `CaptureService.getOwnedCard()` 그대로 재사용(다른 유저 소유
시 404).

### 에러 코드

| 상황 | ErrorCode | HTTP |
| --- | --- | --- |
| body가 1000자 초과 | `INVALID_INPUT`(기존 재사용) | 400 |

## 고려한 대안 (Considered Options)

1. **`updatedAt` 재사용 (기각)** — 다른 필드 변경(즐겨찾기 등)에도
   갱신되어 "body 수정 여부/시각"을 부정확하게 나타냄.
2. **초과 시 조용히 자르기 (기각)** — AI 생성값(LLD-0003)은 사람이
   직접 확인할 수 없어 자르는 방어가 맞았지만, 이번엔 사용자가 직접
   입력한 값이라 조용히 잘리면 사용자가 자기가 뭘 썼는지도 모르게
   됨. 명확히 실패시켜 사용자가 고치게 한다.

## 결과 (Consequences)

### 긍정
- `BODY_MAX_LENGTH` 단일화로 AI 생성/사용자 수정 간 기준 불일치
  위험 제거.
- 기존 소유권 검증·엔티티 검증 패턴 재사용으로 새로운 리스크 적음.

### 부정 / 트레이드오프
- 없음(이번 변경 범위 내에서는 특별한 트레이드오프 없음).

## 후속 / 미결정

- 없음.