package cmc.recap.auth.dto.request;

import cmc.recap.user.domain.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OAuthLoginRequest(
        @NotBlank String deviceId,
        @NotBlank String providerToken,
        @NotNull Platform platform
) {
}
