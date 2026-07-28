package cmc.recap.report.domain;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.global.entity.BaseTimeEntity;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reports", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "capture_id"})
})
@Getter
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

    @Column(name = "detail", length = 200)
    private String detail;

    public static Report create(User user, InfoCard card, ReportReason reason, String detail) {
        validateDetail(detail);
        Report report = new Report();
        report.user = user;
        report.captureId = card.getId();
        report.cardType = card.getType();
        report.title = card.getTitle();
        report.summary = card.getSummary();
        report.reason = reason;
        report.detail = detail;
        return report;
    }

    private static void validateDetail(String detail) {
        if (detail != null && detail.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
