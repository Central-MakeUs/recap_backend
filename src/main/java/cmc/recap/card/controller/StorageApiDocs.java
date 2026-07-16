package cmc.recap.card.controller;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.dto.response.CaptureListResponse;
import cmc.recap.card.dto.response.StorageTypeResponse;
import cmc.recap.global.dto.ApiResponse;
import cmc.recap.global.exception.ApiErrorCodes;
import cmc.recap.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Storage", description = "보관함 (즐겨찾기 · 기타 · 유형별 보기)")
public interface StorageApiDocs {

    @Operation(summary = "즐겨찾기 목록 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<CaptureListResponse>> getFavorites(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(
            summary = "기타 유형 정보카드 목록 조회",
            description = "sort가 없거나 latest/oldest가 아니면 latest(최신순)로 처리한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<CaptureListResponse>> getEtc(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "정렬 기준", example = "latest") String sort);

    @Operation(
            summary = "유형별 보기 목록 조회",
            description = "기타(ETC)를 제외한 유형별 개수와 대표 제목(최대 2개)을 반환한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<List<StorageTypeResponse>>> getTypes(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(
            summary = "유형별 정보카드 목록 조회",
            description = "sort가 없거나 latest/oldest가 아니면 latest(최신순)로 처리한다. typeCode는 ETC를 제외한 CardType 값만 허용한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    @ApiErrorCodes({
            ErrorCode.INVALID_INPUT
    })
    ResponseEntity<ApiResponse<CaptureListResponse>> getTypeCaptures(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회할 정보카드 유형 (ETC 제외)", example = "KNOWLEDGE") CardType typeCode,
            @Parameter(description = "정렬 기준", example = "latest") String sort);
}
