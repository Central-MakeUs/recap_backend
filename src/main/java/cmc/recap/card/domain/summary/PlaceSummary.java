package cmc.recap.card.domain.summary;

public record PlaceSummary(
        String placeName,
        String location,
        String businessHours
) implements CardSummary {
}
