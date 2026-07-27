package cmc.recap.card.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardAnalysisPromptTest {

    @Test
    @DisplayName("build 하면 {BODY_MAX_LENGTH} 플레이스홀더가 실제 숫자로 치환된다")
    void build_하면_플레이스홀더가_실제_숫자로_치환된다() {
        String prompt = CardAnalysisPrompt.build(1000);

        assertThat(prompt).doesNotContain("{BODY_MAX_LENGTH}");
        assertThat(prompt).contains("1000자 이내");
    }

    @Test
    @DisplayName("build 하면 9개 유형 라벨이 전부 포함된다")
    void build_하면_9개_유형_라벨이_전부_포함된다() {
        String prompt = CardAnalysisPrompt.build(CardAnalysisPrompt.BODY_MAX_LENGTH);

        assertThat(prompt).contains(
                "JOB", "SHOPPING", "PLACE", "SCHEDULE", "KNOWLEDGE",
                "CONTENT", "BENEFIT", "RECORD", "ETC");
    }
}
