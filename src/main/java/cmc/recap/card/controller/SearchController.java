package cmc.recap.card.controller;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.SearchScope;
import cmc.recap.card.dto.response.SearchResponse;
import cmc.recap.card.service.SearchService;
import cmc.recap.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController implements SearchApiDocs {

    private final SearchService searchService;

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @AuthenticationPrincipal Long userId,
            @RequestParam String q,
            @RequestParam SearchScope scope,
            @RequestParam(required = false) CardType typeCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SearchResponse response = searchService.search(userId, q, scope, typeCode, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
