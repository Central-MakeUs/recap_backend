package cmc.recap.auth.controller;

import cmc.recap.auth.dto.request.LogoutRequest;
import cmc.recap.auth.dto.request.OAuthLoginRequest;
import cmc.recap.auth.dto.request.TokenRefreshRequest;
import cmc.recap.auth.dto.response.TokenResponse;
import cmc.recap.auth.service.AuthService;
import cmc.recap.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApiDocs {

    private final AuthService authService;

    @PostMapping("/oauth/{provider}/login")
    @Override
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request) {
        TokenResponse response = authService.login(provider, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    @Override
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    @Override
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
