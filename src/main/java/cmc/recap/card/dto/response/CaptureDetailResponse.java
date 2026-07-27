package cmc.recap.card.dto.response;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import java.time.Instant;

public record CaptureDetailResponse(
        Long captureId, CardType typeCode, String title, String summary,
        String body,
        String originalImageUrl, // 원본 이미지 만료(1개월) 시 null. 값이 있는데 로딩 실패한 경우와는 구분해야 함
        boolean isFavorite, Instant organizedAt
) {
    public static CaptureDetailResponse from(InfoCard card, String originalImageUrl) {
        return new CaptureDetailResponse(
                card.getId(), card.getType(), card.getTitle(), card.getSummary(),
                card.getBody(), originalImageUrl, card.isFavorite(), card.getCreatedAt());
    }
}
