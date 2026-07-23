package cmc.recap.card.dto.response;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import java.time.Instant;

public record SearchResultResponse(
        Long captureId, CardType typeCode, String thumbnailUrl,
        String titleHighlighted, String summaryHighlighted,
        String ocrExcerptHighlighted,
        boolean isFavorite, Instant organizedAt
) {
    public static SearchResultResponse of(InfoCard card, String thumbnailUrl,
            String titleHighlighted, String summaryHighlighted, String ocrExcerptHighlighted) {
        return new SearchResultResponse(card.getId(), card.getType(), thumbnailUrl,
                titleHighlighted, summaryHighlighted, ocrExcerptHighlighted,
                card.isFavorite(), card.getCreatedAt());
    }
}
