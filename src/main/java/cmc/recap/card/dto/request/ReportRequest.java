package cmc.recap.card.dto.request;

import cmc.recap.report.domain.ReportReason;
import jakarta.validation.constraints.NotNull;

public record ReportRequest(
        @NotNull ReportReason reason,
        String detail
) {
}
