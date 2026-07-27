package cmc.recap.user.controller;

import cmc.recap.global.dto.ApiResponse;
import cmc.recap.user.dto.response.AccountInfoResponse;
import cmc.recap.user.dto.response.DataSummaryResponse;
import cmc.recap.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApiDocs {

    private final UserService userService;

    @DeleteMapping("/me")
    @Override
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Override
    public ResponseEntity<ApiResponse<AccountInfoResponse>> getAccountInfo(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAccountInfo(userId)));
    }

    @GetMapping("/me/data-summary")
    @Override
    public ResponseEntity<ApiResponse<DataSummaryResponse>> getDataSummary(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getDataSummary(userId)));
    }

    @DeleteMapping("/me/data")
    @Override
    public ResponseEntity<Void> deleteAccountData(@AuthenticationPrincipal Long userId) {
        userService.deleteAccountData(userId);
        return ResponseEntity.noContent().build();
    }
}
