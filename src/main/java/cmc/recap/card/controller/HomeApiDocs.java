package cmc.recap.card.controller;

import cmc.recap.card.dto.response.HomeSummaryResponse;
import cmc.recap.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Home", description = "홈 화면 요약 조회")
public interface HomeApiDocs {

    @Operation(
            summary = "홈 화면 요약 조회",
            description = "최근 정리된 캡처(30일 이내, 최대 3개), 즐겨찾기(최대 3개), "
                    + "자주 저장한 유형(ETC 제외, 최대 4개)을 한 번에 조회한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<HomeSummaryResponse>> getSummary(Long userId);
}
