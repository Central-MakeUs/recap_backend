package cmc.recap.user.dto.response;

import cmc.recap.user.domain.User;
import java.time.Instant;

public record AccountInfoResponse(String platform, Instant createdAt) {
    public static AccountInfoResponse from(User user) {
        return new AccountInfoResponse(
                user.getOauthProvider() == null ? null : user.getOauthProvider().toLowerCase(),
                user.getCreatedAt());
    }
}
