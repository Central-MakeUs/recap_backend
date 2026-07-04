package cmc.recap.card.domain.summary;

public record KnowledgeSummary(
        String coreSummary,
        String source
) implements CardSummary {
}
