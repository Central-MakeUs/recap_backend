package cmc.recap.user.controller;

import cmc.recap.global.dto.ApiResponse;
import cmc.recap.user.dto.response.AccountInfoResponse;
import cmc.recap.user.dto.response.ConsentStatusResponse;
import cmc.recap.user.dto.response.DataSummaryResponse;
import cmc.recap.user.service.ConsentService;
import cmc.recap.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApiDocs {

    private final UserService userService;
    private final ConsentService consentService;

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

    @GetMapping("/me/consent")
    @Override
    public ResponseEntity<ApiResponse<ConsentStatusResponse>> getConsentStatus(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(consentService.getStatus(userId)));
    }

    @PostMapping("/me/consent")
    @Override
    public ResponseEntity<Void> giveConsent(@AuthenticationPrincipal Long userId) {
        consentService.give(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/consent")
    @Override
    public ResponseEntity<Void> withdrawConsent(@AuthenticationPrincipal Long userId) {
        consentService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
