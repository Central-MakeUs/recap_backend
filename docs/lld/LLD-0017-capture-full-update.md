# LLD-0017: 정보카드 전체 필드 수정 API

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-29 |
| 관련 | [LLD-0011](LLD-0011-capture-body-edit.md)(Superseded), [LLD-0005](LLD-0005-capture-detail-favorite-delete.md), [LLD-0008](LLD-0008-body-type-templates.md) |

## 맥락 (Context)

이슈 #33(본문만 수정, LLD-0011)이 개발 완료됐으나, 애초 07-02(정보카드
수정) 화면 설계 의도(제목/유형/요약/본문 전체 수정)로 범위를 되돌리기로
결정(fix#48). `PATCH /captures/{captureId}/body`는 이 기능으로
대체되며 제거한다.

## 결정 (Decision)

### 엔드포인트 통합, 4개 필드 전부 필수

```
PATCH /api/v1/captures/{captureId}
```

부분 수정(PATCH의 일반적 의미)을 허용하지 않는다. title/summary/
body/cardType 4개를 항상 함께 받는다 — 일부만 보낼 때 "안 보낸
필드는 유지인지 빈 값인지"가 애매해지는 문제를 원천 차단한다.

### 필드별 검증 기준 — 원래 스키마 nullable 여부를 그대로 따름

```
title:    빈 값 금지, 1자 이상 30자 이내 (기존 InfoCard 생성 시
          검증 재사용 — 화면 전반에 항상 노출되는 핵심 필드)
summary:  null/빈 문자열 허용, 80자 이내만 체크 (원래 스키마가
          nullable로 설계됨, LLD-0002)
body:     null/빈 문자열 허용, 1000자 이내만 체크 (LLD-0011과 동일)
cardType: 9개 유형 중 자유롭게 선택 가능(ETC 포함), null 금지
          (필드 자체는 필수)
```

**cardType 자유 선택**: AI 분석 시의 "필수값 없으면 ETC로 강제
재분류" 규칙(LLD-0008)은 사용자 직접 편집에는 적용하지 않는다 —
사용자가 자기 카드를 재분류하는 정당한 행위이므로 제약을 두지
않는다.

### `InfoCard.updateBody()`를 `update()`로 대체 (확장 아님)

```java
public void update(String title, String summary, String body, CardType cardType) {
    validateTitle(title); // 기존 title 검증 재사용
    validateSummaryLength(summary);
    validateBodyLength(body);
    this.title = title;
    this.summary = summary;
    this.body = body;
    this.type = cardType;
    this.edited = true;
    this.editedAt = Instant.now();
}
```

`updateBody()`는 삭제한다 — 본문만 다루던 옛 메서드를 남겨두면
"언제 `update()`를 쓰고 언제 `updateBody()`를 쓰는지" 혼란만 생긴다.

`edited`/`editedAt`은 실제 값이 이전과 달라졌는지 필드별로 비교하지
않고, `update()`가 호출되면 무조건 갱신한다 — diff 비교 로직을
넣는 비용이 실익보다 크다고 판단.

### 수정 이력 필드 리네임

`bodyEdited`/`bodyEditedAt` → `edited`/`editedAt` (본문만이 아니라
카드 전체 수정을 나타내는 이름으로).

### 응답

`204 No Content`. 수정된 값이 필요하면 클라이언트가 기존 상세조회
(`GET /captures/{captureId}`)를 다시 호출한다.

### 요청 DTO

```java
public record CaptureUpdateRequest(String title, String summary, String body, CardType cardType) {}
```

기존 `BodyUpdateRequest`는 제거한다.

## 고려한 대안 (Considered Options)

1. **`update()`를 `updateBody()`의 확장(오버로드)으로 유지 (기각)** —
   본문만 다루던 옛 메서드와 카드 전체를 다루는 새 메서드가 공존하면
   호출부에서 혼란이 생김.
2. **summary도 title처럼 빈 값 금지 (기각)** — 원래 스키마(LLD-0002)가
   summary를 nullable로 설계했는데, 이제 와서 필수로 만들면 기존
   설계와 어긋남.
3. **필드별 diff 비교 후 실제 변경 시에만 `edited` 갱신 (기각)** —
   구현 복잡도 대비 실익이 낮음.

## 결과 (Consequences)

### 긍정
- API가 하나로 통합되어 클라이언트가 "본문만 vs 전체" 중 뭘 써야
  할지 헷갈릴 일이 없음.
- 기존 title 검증 로직 재사용으로 신규 코드 최소화.

### 부정 / 트레이드오프
- PATCH이지만 실제로는 전체 필드를 요구하는 방식이라, HTTP 메서드
  의미론(PATCH=부분 수정)과 완전히 일치하지는 않음 — 다만 API
  일관성과 모호함 제거라는 실익이 더 크다고 판단.

## 후속 / 미결정

- 없음.
