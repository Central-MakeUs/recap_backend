# LLD-0010: 부적절한 AI 결과 신고 기능

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-28 |
| 관련 | [ADR-0015](../adr/ADR-0015-user-withdrawal-hybrid-deletion.md), [LLD-0005](LLD-0005-capture-detail-favorite-delete.md), [ADR-0010](../adr/ADR-0010-original-image-s3-storage.md) |

## 맥락 (Context)

AI가 생성한 정보카드 내용이 부적절하거나 틀렸을 때 사용자가 신고할
수 있는 기능이 필요하다. 동아리 프로젝트 규모라 관리자 백오피스가
없어, 신고를 받아도 실시간 조치는 불가능하다. 현실적으로 할 수 있는
건 **누적된 신고 데이터를 근거로 AI 프롬프트를 개선하는 것**뿐이다.

설계 중, `InfoCard`(원본 이미지 포함)가 여러 경로로 삭제될 수 있다는
걸 발견했다: 유저의 단건 삭제(LLD-0005), 회원탈퇴(ADR-0015, 유저의
전체 이미지 하드 삭제), 1개월 자동 만료 배치(추후 이슈), 다중 삭제
(추후 이슈). `Report`가 `InfoCard`에 의존하면 이 중 어떤 경로로든
`InfoCard`가 사라질 때 신고 데이터의 가치가 훼손된다.

## 결정 (Decision)

### 조치 범위 — 기록만, 자동 조치 없음

신고해도 해당 카드를 자동으로 숨기거나 삭제하지 않는다. 신고 사유가
"분류가 틀렸다"처럼 카드 자체는 여전히 유용한 경우가 많고, 삭제를
원하면 유저가 기존 삭제 API(LLD-0005)를 직접 쓰면 된다. 신고는 순수
기록이고, 팀이 주기적으로 `reason`별 집계를 확인해 프롬프트 개선
(LLD-0008 개정)에 반영하는 운영 프로세스로 처리한다.

### `Report`는 `InfoCard`에 진짜 연관관계로 묶지 않는다

```java
@Entity
@Table(name = "reports", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "capture_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "capture_id", nullable = false)
    private Long captureId; // InfoCard로의 JPA 연관관계 아님 — 참조용 숫자만

    // 신고 시점 스냅샷 — InfoCard가 나중에 어떤 경로로든 삭제돼도
    // 패턴 분석이 가능하도록 값 자체를 복사해서 보존
    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "title", nullable = false, length = 30)
    private String title;

    @Column(name = "summary", length = 80)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private ReportReason reason;

    public static Report create(User user, InfoCard card, ReportReason reason) {
        Report report = new Report();
        report.user = user;
        report.captureId = card.getId();
        report.cardType = card.getType();
        report.title = card.getTitle();
        report.summary = card.getSummary();
        report.reason = reason;
        return report;
    }
}
```

**`user`는 진짜 `@ManyToOne`으로 유지한다** — ADR-0015에 따라 유저가
탈퇴해도 `User` row 자체는 익명화된 채 남으므로(하드 삭제 아님), FK
관계가 끊길 위험이 없다. 반면 `InfoCard`는 하드 삭제되는 경로가
여러 개라 `captureId`를 참조용 숫자로만 남긴다.

**이 설계의 알려진 한계**: `title`/`summary`는 남아도 원본 이미지가
사라지면, "패턴 분석"(어떤 유형에서 신고가 몰리는지)은 가능해도
"개별 신고 건이 정확히 왜 틀렸는지 눈으로 확인"하는 건 불가능해진다.
이건 기술로 해결하지 않고, **신고를 1개월(자동 만료 주기)보다
짧은 주기로 확인하면 개별 사례까지 볼 여지가 있다**는 운영 권장
사항으로만 남긴다(강제 아님).

### `ReportReason`

```java
public enum ReportReason {
    WRONG_TYPE,       // 유형 분류가 틀림
    INCORRECT_INFO,   // 정보 자체가 틀림/부정확함
    OFFENSIVE,        // 부적절한 내용
    OTHER             // 기타
}
```

카테고리 방식을 채택한다(자유 텍스트 아님) — 자유 텍스트는 나중에
패턴을 뽑으려면 일일이 읽어야 하지만, 카테고리는 집계 쿼리 하나로
"어떤 사유가 제일 많은지" 바로 확인 가능하다.

### 중복 신고 방지

`(user_id, capture_id)` 유니크 제약으로 DB 레벨에서 막는다. 같은
카드를 같은 유저가 다시 신고하려 하면 조용히 무시하지 않고 명시적으로
막는다(`domain-design-principles.md` #2와 일관).

### API

```
POST /api/v1/captures/{captureId}/report
```

**요청**

```json
{ "reason": "WRONG_TYPE" }
```

**응답**: `204 No Content`

기존 `CaptureController`/`CaptureApiDocs`에 추가한다 — `/captures`
리소스 하위 연산이라 새 컨트롤러를 만들지 않는다(이슈 #15와 동일
판단 근거).

### 에러 코드

| 상황 | ErrorCode | HTTP |
| --- | --- | --- |
| 존재하지 않거나 다른 유저 소유의 captureId | `NOT_FOUND` | 404 |
| 이미 신고한 카드에 재신고 | `ALREADY_REPORTED`(신규) | 409 |

## 고려한 대안 (Considered Options)

1. **신고 시 카드 자동 숨김/삭제 (기각)** — 신고 사유가 분류 오류처럼
   카드 자체는 여전히 유용한 경우가 많음. 삭제는 유저가 원할 때
   기존 API로 직접 하도록 분리.
2. **`Report.captureId`를 `InfoCard`에 대한 진짜 FK로 (기각)** — 여러
   경로(탈퇴, 단건 삭제, 자동 만료, 다중 삭제)로 `InfoCard`가 삭제될
   때 cascade로 신고 데이터가 함께 사라지거나, 삭제 자체가 막히는
   문제 발생.
3. **자유 텍스트 사유 (기각)** — 집계·패턴 분석 목적에 카테고리가
   더 적합.
4. **`Report`에 신고 시점 스냅샷(cardType/title/summary) 보존 (채택)** —
   `InfoCard` 삭제 이후에도 최소한의 분석 가치를 유지.

## 결과 (Consequences)

### 긍정
- `InfoCard`가 어떤 경로로 삭제되든 `Report` 데이터가 영향받지 않음.
- 카테고리 기반이라 프롬프트 개선에 바로 활용 가능한 집계가 쉬움.

### 부정 / 트레이드오프
- 원본 이미지가 사라진 뒤에는 개별 신고 건의 깊은 진단이 불가능
  (스냅샷 텍스트만으로는 한계, 운영으로 완화할 뿐 기술적 해결 아님).
- 관리자 백오피스가 없어, 신고 확인 자체가 DB를 직접 조회하는
  수동 프로세스로 남음.

## 후속 / 미결정

- [ ] 신고 데이터를 주기적으로 확인하는 프로세스(누가, 얼마나
      자주)는 팀 운영 합의 필요
- [ ] Apple App Store 심사 가이드라인 1.2(UGC 신고 요구사항)가
      RECAP처럼 비공개(본인만 보는) 콘텐츠에도 적용되는지 재확인
      필요 — 적용 안 된다면 이 기능 자체의 우선순위 재검토 여지 있음
