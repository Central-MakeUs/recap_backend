package cmc.recap.user.controller;

import cmc.recap.global.dto.ApiResponse;
import cmc.recap.global.exception.ApiErrorCodes;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.user.dto.response.AccountInfoResponse;
import cmc.recap.user.dto.response.DataSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "User", description = "회원 관리")
public interface UserApiDocs {

    @Operation(summary = "회원탈퇴")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "탈퇴 성공"))
    @ApiErrorCodes({
            ErrorCode.USER_NOT_FOUND,
            ErrorCode.ALREADY_WITHDRAWN
    })
    ResponseEntity<Void> withdraw(Long userId);

    @Operation(summary = "계정 정보 조회", description = "로그인 플랫폼과 가입일을 조회한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<AccountInfoResponse>> getAccountInfo(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(summary = "데이터 관리 요약 조회", description = "정리된 캡처 개수를 조회한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ApiResponse<DataSummaryResponse>> getDataSummary(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(
            summary = "계정 데이터 전체 삭제",
            description = "캡처와 원본 이미지를 모두 삭제한다. 계정과 로그인 세션은 유지된다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"))
    ResponseEntity<Void> deleteAccountData(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}
