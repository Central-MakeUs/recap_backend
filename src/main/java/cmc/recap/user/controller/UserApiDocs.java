package cmc.recap.user.controller;

import cmc.recap.global.exception.ApiErrorCodes;
import cmc.recap.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

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
}
