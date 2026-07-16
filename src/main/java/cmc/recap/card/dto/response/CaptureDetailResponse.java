package cmc.recap.card.dto.response;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import java.time.Instant;

public record CaptureDetailResponse(
        Long captureId, CardType typeCode, String title, String summary,
        String body, String originalImageUrl, boolean isFavorite, Instant organizedAt
) {
    public static CaptureDetailResponse from(InfoCard card, String originalImageUrl) {
        return new CaptureDetailResponse(
                card.getId(), card.getType(), card.getTitle(), card.getSummary(),
                card.getBody(), originalImageUrl, card.isFavorite(), card.getCreatedAt());
    }
}
