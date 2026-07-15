package cmc.recap.card.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UploadUrlsRequest(
        @Min(1) @Max(20) int count
) {
}
