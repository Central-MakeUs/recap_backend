package cmc.recap.card.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SearchHighlighterTest {

    @Nested
    @DisplayName("highlight")
    class Highlight {

        @Test
        @DisplayName("매칭되면 첫 번째 매칭 구간만 mark로 감싼다")
        void 매칭되면_첫_번째_매칭_구간만_mark로_감싼다() {
            String result = SearchHighlighter.highlight("cat and cat", "cat");

            assertThat(result).isEqualTo("<mark>cat</mark> and cat");
        }

        @Test
        @DisplayName("대소문자가 달라도 매칭하되 원문 대소문자를 유지한다")
        void 대소문자가_달라도_매칭하되_원문_대소문자를_유지한다() {
            String result = SearchHighlighter.highlight("HELLO WORLD", "world");

            assertThat(result).isEqualTo("HELLO <mark>WORLD</mark>");
        }

        @Test
        @DisplayName("매칭이 없으면 원문 그대로 반환한다")
        void 매칭이_없으면_원문_그대로_반환한다() {
            String result = SearchHighlighter.highlight("hello", "xyz");

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("text가 null이면 null을 반환한다")
        void text가_null이면_null을_반환한다() {
            String result = SearchHighlighter.highlight(null, "cat");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("excerptOcrMatch")
    class ExcerptOcrMatch {

        @Test
        @DisplayName("매칭이 없으면 null을 반환한다")
        void 매칭이_없으면_null을_반환한다() {
            String result = SearchHighlighter.excerptOcrMatch("hello world", "xyz");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("매칭 지점 앞뒤가 20자를 넘지 않으면 잘림 없이 전체를 감싼다")
        void 매칭_지점_앞뒤가_20자를_넘지_않으면_잘림_없이_전체를_감싼다() {
            String result = SearchHighlighter.excerptOcrMatch("hello cat world", "cat");

            assertThat(result).isEqualTo("hello <mark>cat</mark> world");
        }

        @Test
        @DisplayName("매칭 지점 앞뒤가 20자를 넘으면 양쪽을 잘라내고 말줄임표를 붙인다")
        void 매칭_지점_앞뒤가_20자를_넘으면_양쪽을_잘라내고_말줄임표를_붙인다() {
            String extractedText = "x".repeat(30) + "cat" + "y".repeat(30);

            String result = SearchHighlighter.excerptOcrMatch(extractedText, "cat");

            String expected = "…" + "x".repeat(20) + "<mark>cat</mark>" + "y".repeat(20) + "…";
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("이모지가 포함된 텍스트에서 서로게이트 쌍을 깨지 않고 자른다")
        void 이모지가_포함된_텍스트에서_서로게이트_쌍을_깨지_않고_자른다() {
            String extractedText = "😀".repeat(25) + "cat" + "😀".repeat(25);

            String result = SearchHighlighter.excerptOcrMatch(extractedText, "cat");

            String expected = "…" + "😀".repeat(20) + "<mark>cat</mark>"
                    + "😀".repeat(20) + "…";
            assertThat(result).isEqualTo(expected);
        }
    }
}
