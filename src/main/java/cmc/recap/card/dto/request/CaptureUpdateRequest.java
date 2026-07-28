package cmc.recap.card.dto.request;

import cmc.recap.card.domain.CardType;

public record CaptureUpdateRequest(String title, String summary, String body, CardType cardType) {
}
