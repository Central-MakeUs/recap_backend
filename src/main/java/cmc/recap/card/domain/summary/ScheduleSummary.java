package cmc.recap.card.domain.summary;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleSummary(
        LocalDate date,
        LocalTime time,
        String location,
        String reservationNumber
) implements CardSummary {
}
