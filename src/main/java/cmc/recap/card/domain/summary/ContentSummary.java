package cmc.recap.card.domain.summary;

public record ContentSummary(
        String title,
        String author,
        String keySentence
) implements CardSummary {
}
