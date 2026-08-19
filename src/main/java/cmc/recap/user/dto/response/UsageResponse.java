package cmc.recap.user.dto.response;

import java.time.Instant;

public record UsageResponse(int usedCount, int limit, int remaining, Instant resetAt) {
    public static UsageResponse of(int usedCount, int limit, Instant resetAt) {
        int remaining = Math.max(0, limit - usedCount);
        return new UsageResponse(usedCount, limit, remaining, resetAt);
    }
}
