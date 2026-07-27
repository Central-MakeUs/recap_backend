package cmc.recap.card.controller;

import cmc.recap.card.dto.response.CapturePageResponse;
import cmc.recap.card.dto.response.HomeSummaryResponse;
import cmc.recap.card.service.HomeService;
import cmc.recap.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController implements HomeApiDocs {

    private final HomeService homeService;

    @GetMapping("/summary")
    @Override
    public ResponseEntity<ApiResponse<HomeSummaryResponse>> getSummary(@AuthenticationPrincipal Long userId) {
        HomeSummaryResponse response = homeService.getSummary(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/recent-captures")
    @Override
    public ResponseEntity<ApiResponse<CapturePageResponse>> getRecentCapturesPage(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CapturePageResponse response = homeService.getRecentCapturesPage(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
