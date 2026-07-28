package cmc.recap.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportTest {

    @Test
    @DisplayName("detail을 포함해 생성하면 신고 시점 스냅샷과 detail이 저장된다")
    void detail을_포함해_생성하면_신고_시점_스냅샷과_detail이_저장된다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        InfoCard card = cardWithId(10L, user);

        Report report = Report.create(user, card, ReportReason.INACCURATE_CONTENT, "가격 정보가 실제와 달라요");

        assertThat(report.getUser()).isEqualTo(user);
        assertThat(report.getCaptureId()).isEqualTo(10L);
        assertThat(report.getCardType()).isEqualTo(CardType.JOB);
        assertThat(report.getTitle()).isEqualTo("title");
        assertThat(report.getSummary()).isEqualTo("summary");
        assertThat(report.getReason()).isEqualTo(ReportReason.INACCURATE_CONTENT);
        assertThat(report.getDetail()).isEqualTo("가격 정보가 실제와 달라요");
    }

    @Test
    @DisplayName("detail이 200자를 초과하면 INVALID_INPUT을 던진다")
    void detail이_200자를_초과하면_INVALID_INPUT을_던진다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        InfoCard card = cardWithId(10L, user);
        String tooLong = "a".repeat(201);

        assertThatThrownBy(() -> Report.create(user, card, ReportReason.INACCURATE_CONTENT, tooLong))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private InfoCard cardWithId(Long id, User owner) {
        InfoCard card = InfoCard.create(
                owner, CardType.JOB, "title", "summary", "body", "captures/1/a.jpg", "extracted", null);
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }
}
