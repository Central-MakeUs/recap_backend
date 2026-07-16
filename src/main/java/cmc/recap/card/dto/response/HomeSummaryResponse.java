package cmc.recap.card.dto.response;

import java.util.List;

public record HomeSummaryResponse(
        List<CaptureSummaryResponse> recentCaptures,
        List<CaptureSummaryResponse> favorites,
        List<TopTypeResponse> topTypes,
        boolean hasAnyCapture
) {
    public static HomeSummaryResponse of(
            List<CaptureSummaryResponse> recentCaptures,
            List<CaptureSummaryResponse> favorites,
            List<TopTypeResponse> topTypes,
            boolean hasAnyCapture) {
        return new HomeSummaryResponse(recentCaptures, favorites, topTypes, hasAnyCapture);
    }
}
