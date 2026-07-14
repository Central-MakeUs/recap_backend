package cmc.recap.card.image;

import cmc.recap.card.domain.CardType;

public record ImageAnalysisResult(
        CardType type, String title, String summary, String body, String extractedText
) {}
