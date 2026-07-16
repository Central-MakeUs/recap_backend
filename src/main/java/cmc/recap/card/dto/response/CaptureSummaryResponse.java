package cmc.recap.card.dto.response;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import java.time.Instant;

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
