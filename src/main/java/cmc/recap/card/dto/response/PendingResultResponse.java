package cmc.recap.card.dto.response;

import cmc.recap.card.domain.BatchStatus;

public record PendingResultResponse(Long batchId, BatchStatus status, int successCount, int failCount) {

    public static PendingResultResponse of(Long batchId, BatchStatus status, int successCount, int failCount) {
        return new PendingResultResponse(batchId, status, successCount, failCount);
    }
}
