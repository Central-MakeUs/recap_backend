package cmc.recap.auth.controller;

import cmc.recap.auth.dto.request.LogoutRequest;
import cmc.recap.auth.dto.request.OAuthLoginRequest;
import cmc.recap.auth.dto.request.TokenRefreshRequest;
import cmc.recap.auth.dto.response.TokenResponse;
import cmc.recap.global.exception.ApiErrorCodes;
import cmc.recap.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "카카오/Apple 소셜 로그인 및 토큰 관리")
public interface AuthApiDocs {

    @Operation(summary = "소셜 로그인/가입")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "로그인/가입 성공"))
    @ApiErrorCodes({
            ErrorCode.INVALID_INPUT,
            ErrorCode.OAUTH_VERIFICATION_FAILED,
            ErrorCode.ALREADY_LINKED_OAUTH
    })
    ResponseEntity<cmc.recap.global.dto.ApiResponse<TokenResponse>> login(
            String provider, OAuthLoginRequest request);

    @Operation(summary = "Access 토큰 재발급")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "재발급 성공"))
    @ApiErrorCodes({
            ErrorCode.INVALID_REFRESH_TOKEN,
            ErrorCode.EXPIRED_REFRESH_TOKEN,
            ErrorCode.USER_NOT_FOUND
    })
    ResponseEntity<cmc.recap.global.dto.ApiResponse<TokenResponse>> refresh(TokenRefreshRequest request);

    @Operation(summary = "로그아웃")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "로그아웃 성공"))
    ResponseEntity<cmc.recap.global.dto.ApiResponse<Void>> logout(LogoutRequest request);
}
