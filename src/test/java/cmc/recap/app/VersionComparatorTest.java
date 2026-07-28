package cmc.recap.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VersionComparatorTest {

    @Nested
    @DisplayName("isLowerThan")
    class IsLowerThan {

        @Test
        @DisplayName("자리수가 다른 버전을 숫자로 비교하면 문자열 비교와 다른 정확한 결과를 낸다")
        void 자리수가_다른_버전을_숫자로_비교하면_정확한_결과를_낸다() {
            // "1.10.0" < "1.2.0"(문자열 비교)이지만 실제로는 "1.10.0"이 더 높은 버전
            boolean result = VersionComparator.isLowerThan("1.10.0", "1.2.0");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("동일한 버전이면 false를 반환한다")
        void 동일한_버전이면_false를_반환한다() {
            boolean result = VersionComparator.isLowerThan("1.2.0", "1.2.0");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("버전이 최소 버전보다 낮으면 true를 반환한다")
        void 버전이_최소_버전보다_낮으면_true를_반환한다() {
            boolean result = VersionComparator.isLowerThan("1.0.0", "1.2.0");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("버전이 최소 버전보다 높으면 false를 반환한다")
        void 버전이_최소_버전보다_높으면_false를_반환한다() {
            boolean result = VersionComparator.isLowerThan("2.0.0", "1.2.0");

            assertThat(result).isFalse();
        }
    }
}
