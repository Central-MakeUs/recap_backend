package cmc.recap.card.controller;

import cmc.recap.card.dto.request.FavoriteRequest;
import cmc.recap.card.dto.request.OrganizeRequest;
import cmc.recap.card.dto.request.UploadUrlsRequest;
import cmc.recap.card.dto.response.CaptureDetailResponse;
import cmc.recap.card.dto.response.OrganizeResponse;
import cmc.recap.card.dto.response.OrganizeStatusResponse;
import cmc.recap.card.dto.response.PendingResultResponse;
import cmc.recap.card.dto.response.UploadUrlsResponse;
import cmc.recap.card.service.CaptureService;
import cmc.recap.card.service.OrganizeService;
import cmc.recap.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/captures")
@RequiredArgsConstructor
public class CaptureController implements CaptureApiDocs {

    private final CaptureService captureService;
    private final OrganizeService organizeService;

    @PostMapping("/upload-urls")
    @Override
    public ResponseEntity<ApiResponse<UploadUrlsResponse>> issueUploadUrls(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody UploadUrlsRequest request) {
        UploadUrlsResponse response = captureService.issueUploadUrls(userId, request.count());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/organize")
    @Override
    public ResponseEntity<ApiResponse<OrganizeResponse>> organize(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody OrganizeRequest request) {
        OrganizeResponse response = organizeService.organize(userId, request.imageKeys());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/organize/{batchId}/status")
    @Override
    public ResponseEntity<ApiResponse<OrganizeStatusResponse>> getOrganizeStatus(
            @AuthenticationPrincipal Long userId, @PathVariable Long batchId) {
        OrganizeStatusResponse response = organizeService.getStatus(userId, batchId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/organize/{batchId}/cancel")
    @Override
    public ResponseEntity<Void> cancelOrganize(
            @AuthenticationPrincipal Long userId, @PathVariable Long batchId) {
        organizeService.cancel(userId, batchId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/organize/pending-result")
    @Override
    public ResponseEntity<ApiResponse<PendingResultResponse>> getPendingResult(
            @AuthenticationPrincipal Long userId) {
        PendingResultResponse response = organizeService.getPendingResult(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/organize/{batchId}/ack")
    @Override
    public ResponseEntity<Void> ackOrganizeResult(
            @AuthenticationPrincipal Long userId, @PathVariable Long batchId) {
        organizeService.ack(userId, batchId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{captureId}")
    @Override
    public ResponseEntity<ApiResponse<CaptureDetailResponse>> getDetail(
            @AuthenticationPrincipal Long userId, @PathVariable Long captureId) {
        CaptureDetailResponse response = captureService.getDetail(userId, captureId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{captureId}/favorite")
    @Override
    public ResponseEntity<Void> updateFavorite(
            @AuthenticationPrincipal Long userId, @PathVariable Long captureId,
            @Valid @RequestBody FavoriteRequest request) {
        captureService.updateFavorite(userId, captureId, request.isFavorite());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{captureId}")
    @Override
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId, @PathVariable Long captureId) {
        captureService.delete(userId, captureId);
        return ResponseEntity.noContent().build();
    }
}
