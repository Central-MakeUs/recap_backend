package cmc.recap.card.dto.response;

import java.util.List;

public record CaptureListResponse(int count, List<CaptureSummaryResponse> items) {
    public static CaptureListResponse of(List<CaptureSummaryResponse> items) {
        return new CaptureListResponse(items.size(), items);
    }
}
