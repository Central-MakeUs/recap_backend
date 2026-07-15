package cmc.recap.card.image;

import java.util.UUID;

public final class CaptureObjectKeyGenerator {

    private static final String PREFIX_FORMAT = "captures/%d/";

    private CaptureObjectKeyGenerator() {
    }

    public static String generate(Long userId) {
        return PREFIX_FORMAT.formatted(userId) + UUID.randomUUID() + ".jpg";
    }

    public static boolean belongsTo(String objectKey, Long userId) {
        return objectKey != null && objectKey.startsWith(PREFIX_FORMAT.formatted(userId));
    }
}
