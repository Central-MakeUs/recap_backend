package cmc.recap.card.domain.summary;

import java.time.LocalDate;

public record JobSummary(
        String companyName,
        String jobTitle,
        LocalDate deadline,
        String qualification
) implements CardSummary {
}
