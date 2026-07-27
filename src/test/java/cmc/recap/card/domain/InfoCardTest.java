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

    @Test
    @DisplayName("updateBody는 정상 수정 시 bodyEdited를 true로, bodyEditedAt을 갱신한다")
    void updateBody는_정상_수정_시_bodyEdited를_true로_bodyEditedAt을_갱신한다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.updateBody("수정된 본문");

        assertThat(card.getBody()).isEqualTo("수정된 본문");
        assertThat(card.isBodyEdited()).isTrue();
        assertThat(card.getBodyEditedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateBody는 1000자를 초과하면 예외를 던진다")
    void updateBody는_1000자를_초과하면_예외를_던진다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);
        String tooLongBody = "가".repeat(InfoCard.BODY_MAX_LENGTH + 1);

        assertThatThrownBy(() -> card.updateBody(tooLongBody))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("updateBody는 null을 허용한다")
    void updateBody는_null을_허용한다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.updateBody(null);

        assertThat(card.getBody()).isNull();
        assertThat(card.isBodyEdited()).isTrue();
    }

    @Test
    @DisplayName("updateBody는 공백을 허용한다")
    void updateBody는_공백을_허용한다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.updateBody("   ");

        assertThat(card.getBody()).isEqualTo("   ");
        assertThat(card.isBodyEdited()).isTrue();
    }
}
