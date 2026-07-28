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
    @DisplayName("update는 정상 수정 시 edited를 true로, editedAt을 갱신한다")
    void update는_정상_수정_시_edited를_true로_editedAt을_갱신한다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.update("수정된 제목", "수정된 요약", "수정된 본문", CardType.CONTENT);

        assertThat(card.getTitle()).isEqualTo("수정된 제목");
        assertThat(card.getSummary()).isEqualTo("수정된 요약");
        assertThat(card.getBody()).isEqualTo("수정된 본문");
        assertThat(card.getType()).isEqualTo(CardType.CONTENT);
        assertThat(card.isEdited()).isTrue();
        assertThat(card.getEditedAt()).isNotNull();
    }

    @Test
    @DisplayName("update는 title이 빈 값이면 예외를 던진다")
    void update는_title이_빈_값이면_예외를_던진다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        assertThatThrownBy(() -> card.update("", "summary", "body", CardType.KNOWLEDGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("update는 summary가 null이면 허용한다")
    void update는_summary가_null이면_허용한다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.update("title", null, "body", CardType.KNOWLEDGE);

        assertThat(card.getSummary()).isNull();
    }

    @Test
    @DisplayName("update는 summary가 80자를 초과하면 예외를 던진다")
    void update는_summary가_80자를_초과하면_예외를_던진다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);
        String tooLongSummary = "가".repeat(InfoCard.SUMMARY_MAX_LENGTH + 1);

        assertThatThrownBy(() -> card.update("title", tooLongSummary, "body", CardType.KNOWLEDGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("update는 body가 null이면 허용한다")
    void update는_body가_null이면_허용한다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.update("title", "summary", null, CardType.KNOWLEDGE);

        assertThat(card.getBody()).isNull();
    }

    @Test
    @DisplayName("update는 body가 1000자를 초과하면 예외를 던진다")
    void update는_body가_1000자를_초과하면_예외를_던진다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);
        String tooLongBody = "가".repeat(InfoCard.BODY_MAX_LENGTH + 1);

        assertThatThrownBy(() -> card.update("title", "summary", tooLongBody, CardType.KNOWLEDGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("update는 cardType이 null이면 예외를 던진다")
    void update는_cardType이_null이면_예외를_던진다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        assertThatThrownBy(() -> card.update("title", "summary", "body", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("update는 cardType을 ETC로 자유롭게 재분류할 수 있다")
    void update는_cardType을_ETC로_자유롭게_재분류할_수_있다() {
        InfoCard card = InfoCard.create(
                user, CardType.KNOWLEDGE, "title", "summary", "body",
                "captures/1/uuid.jpg", "extracted", null);

        card.update("title", "summary", "body", CardType.ETC);

        assertThat(card.getType()).isEqualTo(CardType.ETC);
    }
}
