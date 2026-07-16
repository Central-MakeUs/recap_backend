package cmc.recap.card.dto.response;

import cmc.recap.card.domain.CardType;

public record TopTypeResponse(CardType typeCode, long count, String representativeThumbnailUrl) {

    public static TopTypeResponse of(CardType typeCode, long count, String representativeThumbnailUrl) {
        return new TopTypeResponse(typeCode, count, representativeThumbnailUrl);
    }
}
