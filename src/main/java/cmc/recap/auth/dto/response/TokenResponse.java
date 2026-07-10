package cmc.recap.auth.dto.response;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt
) {

    public static TokenResponse of(String accessToken, String refreshToken, Instant accessTokenExpiresAt) {
        return new TokenResponse(accessToken, refreshToken, accessTokenExpiresAt);
    }
}
