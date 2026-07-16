package cmc.recap.card.controller;

import cmc.recap.card.dto.request.FavoriteRequest;
import cmc.recap.card.dto.request.OrganizeRequest;
import cmc.recap.card.dto.request.UploadUrlsRequest;
import cmc.recap.card.dto.response.CaptureDetailResponse;
import cmc.recap.card.dto.response.OrganizeResponse;
import cmc.recap.card.dto.response.OrganizeStatusResponse;
import cmc.recap.card.dto.response.PendingResultResponse;
import cmc.recap.card.dto.response.UploadUrlsResponse;
import cmc.recap.global.dto.ApiResponse;
import cmc.recap.global.exception.ApiErrorCodes;
import cmc.recap.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Capture", description = "스크린샷 업로드 및 정리")
public interface CaptureApiDocs {

    @Operation(summary = "업로드용 presigned URL 발급")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공"))
    @ApiErrorCodes({
            ErrorCode.INVALID_INPUT
    })
    ResponseEntity<ApiResponse<UploadUrlsResponse>> issueUploadUrls(
            Long userId, UploadUrlsRequest request);

    @Operation(summary = "정리 시작 (배치 생성)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "배치 생성 성공"))
    @ApiErrorCodes({
            ErrorCode.INVALID_INPUT,
            ErrorCode.ORGANIZE_IN_PROGRESS
    })
    ResponseEntity<ApiResponse<OrganizeResponse>> organize(
            Long userId, OrganizeRequest request);

    @Operation(summary = "정리 상태 조회 (폴링)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    @ApiErrorCodes({
            ErrorCode.NOT_FOUND
    })
    ResponseEntity<ApiResponse<OrganizeStatusResponse>> getOrganizeStatus(
            Long userId, Long batchId);

    @Operation(summary = "정리 취소")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "취소 성공"))
    @ApiErrorCodes({
            ErrorCode.NOT_FOUND
    })
    ResponseEntity<Void> cancelOrganize(Long userId, Long batchId);

    @Operation(summary = "이탈 후 복귀 시 결과 확인")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<PendingResultResponse>> getPendingResult(Long userId);

    @Operation(summary = "정리 결과 확인 처리 (토스트 재노출 방지)")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "확인 처리 성공"))
    @ApiErrorCodes({
            ErrorCode.NOT_FOUND
    })
    ResponseEntity<Void> ackOrganizeResult(Long userId, Long batchId);

    @Operation(summary = "정보카드 상세 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    @ApiErrorCodes({
            ErrorCode.NOT_FOUND
    })
    ResponseEntity<ApiResponse<CaptureDetailResponse>> getDetail(Long userId, Long captureId);

    @Operation(summary = "즐겨찾기 설정/해제")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "변경 성공"))
    @ApiErrorCodes({
            ErrorCode.NOT_FOUND
    })
    ResponseEntity<Void> updateFavorite(Long userId, Long captureId, FavoriteRequest request);

    @Operation(summary = "정보카드 삭제")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"))
    @ApiErrorCodes({
            ErrorCode.NOT_FOUND
    })
    ResponseEntity<Void> delete(Long userId, Long captureId);
}
