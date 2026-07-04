package cmc.recap.card.domain.summary;

public record ShoppingSummary(
        String productName,
        Integer price,
        String brand
) implements CardSummary {
}
