package cmc.recap.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InfoCardTest {

    private final User user = User.createByDevice("device-1", Platform.ANDROID);

    @Test
    @DisplayName("title이 null이면 예외를 던진다")
    void title이_null이면_예외를_던진다() {
        assertThatThrownBy(() -> InfoCard.create(
                user, CardType.KNOWLEDGE, null, "summary", "body",
                "captures/1/uuid.jpg", "extracted", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("title이 공백이면 예외를 던진다")
    void title이_공백이면_예외를_던진다() {
        assertThatThrownBy(() -> InfoCard.create(
                user, CardType.KNOWLEDGE, "   ", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("title이 30자를 초과하면 예외를 던진다")
    void title이_30자를_초과하면_예외를_던진다() {
        String tooLongTitle = "가".repeat(31);

        assertThatThrownBy(() -> InfoCard.create(
                user, CardType.KNOWLEDGE, tooLongTitle, "summary", "body",
                "captures/1/uuid.jpg", "extracted", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("markFavorite(true)를 연속 호출해도 즐겨찾기 상태는 동일하다")
    void markFavorite_true를_연속_호출해도_즐겨찾기_상태는_동일하다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.markFavorite(true);
        card.markFavorite(true);

        assertThat(card.isFavorite()).isTrue();
        assertThat(card.getFavoritedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFavorite(false)를 연속 호출해도 즐겨찾기 상태는 동일하다")
    void markFavorite_false를_연속_호출해도_즐겨찾기_상태는_동일하다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.markFavorite(false);
        card.markFavorite(false);

        assertThat(card.isFavorite()).isFalse();
        assertThat(card.getFavoritedAt()).isNull();
    }
}
