package cmc.recap.card.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaptureObjectKeyGeneratorTest {

    @Test
    @DisplayName("generate 하면 captures/{userId}/{uuid}.jpg 형식의 objectKey를 만든다")
    void generate_하면_captures_userId_uuid_jpg_형식의_objectKey를_만든다() {
        String objectKey = CaptureObjectKeyGenerator.generate(1L);

        assertThat(objectKey).matches("captures/1/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");
    }

    @Test
    @DisplayName("generate를 반복 호출하면 매번 다른 objectKey를 만든다")
    void generate를_반복_호출하면_매번_다른_objectKey를_만든다() {
        String first = CaptureObjectKeyGenerator.generate(1L);
        String second = CaptureObjectKeyGenerator.generate(1L);

        assertThat(first).isNotEqualTo(second);
    }
}
