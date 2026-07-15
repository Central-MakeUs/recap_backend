package cmc.recap.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrganizeServiceTest {

    private final OrganizeService organizeService = new OrganizeService();

    @Test
    @DisplayName("title이 30자 이하이면 그대로 반환한다")
    void title이_30자_이하이면_그대로_반환한다() {
        String title = "가".repeat(30);

        String result = organizeService.normalizeTitle(title);

        assertThat(result).isEqualTo(title);
    }

    @Test
    @DisplayName("title이 30자를 초과하면 29자로 자르고 말줄임표를 붙인다")
    void title이_30자를_초과하면_29자로_자르고_말줄임표를_붙인다() {
        String title = "가".repeat(35);

        String result = organizeService.normalizeTitle(title);

        assertThat(result).isEqualTo("가".repeat(29) + "…");
        assertThat(result.codePointCount(0, result.length())).isEqualTo(30);
    }

    @Test
    @DisplayName("title에 이모지가 포함돼 30자를 초과하면 서로게이트 쌍을 깨지 않고 자른다")
    void title에_이모지가_포함돼_30자를_초과하면_서로게이트_쌍을_깨지_않고_자른다() {
        String title = "가".repeat(29) + "😀" + "나";

        String result = organizeService.normalizeTitle(title);

        assertThat(result).isEqualTo("가".repeat(29) + "…");
        assertThat(result.codePointCount(0, result.length())).isEqualTo(30);
    }

    @Test
    @DisplayName("summary가 80자 이하이면 그대로 반환한다")
    void summary가_80자_이하이면_그대로_반환한다() {
        String summary = "가".repeat(80);

        String result = organizeService.normalizeSummary(summary);

        assertThat(result).isEqualTo(summary);
    }

    @Test
    @DisplayName("summary가 80자를 초과하면 79자로 자르고 말줄임표를 붙인다")
    void summary가_80자를_초과하면_79자로_자르고_말줄임표를_붙인다() {
        String summary = "가".repeat(85);

        String result = organizeService.normalizeSummary(summary);

        assertThat(result).isEqualTo("가".repeat(79) + "…");
        assertThat(result.codePointCount(0, result.length())).isEqualTo(80);
    }
}
