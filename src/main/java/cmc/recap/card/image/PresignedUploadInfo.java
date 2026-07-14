package cmc.recap.card.image;

import java.time.Instant;

public record PresignedUploadInfo(String uploadUrl, Instant expiresAt) {}
