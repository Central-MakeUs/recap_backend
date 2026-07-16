package cmc.recap.card.controller;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.dto.response.CaptureListResponse;
import cmc.recap.card.dto.response.StorageTypeResponse;
import cmc.recap.card.service.StorageService;
import cmc.recap.global.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController implements StorageApiDocs {

    private final StorageService storageService;

    @GetMapping("/favorites")
    @Override
    public ResponseEntity<ApiResponse<CaptureListResponse>> getFavorites(
            @AuthenticationPrincipal Long userId) {
        CaptureListResponse response = storageService.getFavorites(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/etc")
    @Override
    public ResponseEntity<ApiResponse<CaptureListResponse>> getEtc(
            @AuthenticationPrincipal Long userId, @RequestParam(required = false) String sort) {
        CaptureListResponse response = storageService.getEtc(userId, sort);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/types")
    @Override
    public ResponseEntity<ApiResponse<List<StorageTypeResponse>>> getTypes(
            @AuthenticationPrincipal Long userId) {
        List<StorageTypeResponse> response = storageService.getTypes(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/types/{typeCode}/captures")
    @Override
    public ResponseEntity<ApiResponse<CaptureListResponse>> getTypeCaptures(
            @AuthenticationPrincipal Long userId, @PathVariable CardType typeCode,
            @RequestParam(required = false) String sort) {
        CaptureListResponse response = storageService.getTypeDetail(userId, typeCode, sort);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
