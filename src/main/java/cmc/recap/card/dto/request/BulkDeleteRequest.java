package cmc.recap.card.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkDeleteRequest(
        @NotNull List<Long> captureIds
) {
}
