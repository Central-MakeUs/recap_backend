package cmc.recap.user.dto.response;

public record DataSummaryResponse(long capturedCount) {
    public static DataSummaryResponse of(long capturedCount) {
        return new DataSummaryResponse(capturedCount);
    }
}
