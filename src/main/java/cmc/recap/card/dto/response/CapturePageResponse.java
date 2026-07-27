package cmc.recap.card.dto.response;

import java.util.List;

public record CapturePageResponse(long count, boolean hasNext, List<CaptureSummaryResponse> items) {
    public static CapturePageResponse of(long count, boolean hasNext, List<CaptureSummaryResponse> items) {
        return new CapturePageResponse(count, hasNext, items);
    }
}
