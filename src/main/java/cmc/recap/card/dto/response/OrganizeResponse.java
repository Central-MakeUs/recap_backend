package cmc.recap.card.dto.response;

import cmc.recap.card.domain.BatchStatus;

public record OrganizeResponse(Long batchId, int totalCount, BatchStatus status) {

    public static OrganizeResponse of(Long batchId, int totalCount, BatchStatus status) {
        return new OrganizeResponse(batchId, totalCount, status);
    }
}
