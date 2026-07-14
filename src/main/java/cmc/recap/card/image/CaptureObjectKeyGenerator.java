package cmc.recap.card.image;

import java.util.UUID;

public final class CaptureObjectKeyGenerator {

    private CaptureObjectKeyGenerator() {
    }

    public static String generate(Long userId) {
        return "captures/%d/%s.jpg".formatted(userId, UUID.randomUUID());
    }
}
