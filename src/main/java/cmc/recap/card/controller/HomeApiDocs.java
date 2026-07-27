package cmc.recap.card.controller;

import cmc.recap.card.dto.response.CapturePageResponse;
import cmc.recap.card.dto.response.HomeSummaryResponse;
import cmc.recap.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Home", description = "홈 화면 요약 조회")
public interface HomeApiDocs {

    @Operation(
            summary = "홈 화면 요약 조회",
            description = "최근 정리된 캡처(30일 이내, 최대 3개), 즐겨찾기(최대 3개), "
                    + "자주 저장한 유형(ETC 제외, 최대 4개)을 한 번에 조회한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<HomeSummaryResponse>> getSummary(Long userId);

    @Operation(
            summary = "최근 정리된 캡처 전체 목록 조회",
            description = "홈의 '최근 정리된 스크린샷' 미리보기(최대 3개)와 별개로, "
                    + "최근 30일 이내 정리 완료된 캡처 전체를 최신순으로 페이지네이션 조회한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<CapturePageResponse>> getRecentCapturesPage(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") int size);
}
