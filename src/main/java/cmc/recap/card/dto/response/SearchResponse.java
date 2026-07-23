package cmc.recap.card.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record SearchResponse(long count, boolean hasNext, List<SearchResultResponse> items) {
    public static SearchResponse of(Page<SearchResultResponse> page) {
        return new SearchResponse(page.getTotalElements(), page.hasNext(), page.getContent());
    }
}
