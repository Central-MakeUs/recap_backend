package cmc.recap.card.dto.response;

import cmc.recap.card.domain.BatchStatus;

public record OrganizeStatusResponse(
        Long batchId,
        BatchStatus status,
        int totalCount,
        int successCount,
        int failCount
) {

    public static OrganizeStatusResponse of(
            Long batchId, BatchStatus status, int totalCount, int successCount, int failCount
    ) {
        return new OrganizeStatusResponse(batchId, status, totalCount, successCount, failCount);
    }
}
