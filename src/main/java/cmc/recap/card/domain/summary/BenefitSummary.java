package cmc.recap.card.domain.summary;

import java.time.LocalDate;

public record BenefitSummary(
        String benefitName,
        LocalDate deadline,
        String condition
) implements CardSummary {
}
