package cmc.recap.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AppVersionServiceTest {

    private final AppVersionService sut = new AppVersionService(
            "1.2.0", "https://ios-update.example.com",
            "1.3.0", "https://android-update.example.com");

    @Nested
    @DisplayName("checkVersion")
    class CheckVersion {

        @Test
        @DisplayName("platform이 IOS(대소문자 무시)이면 iOS 환경변수 값을 반환한다")
        void platform이_IOS이면_iOS_환경변수_값을_반환한다() {
            VersionCheckResponse response = sut.checkVersion("ios", "1.0.0");

            assertThat(response.minimumVersion()).isEqualTo("1.2.0");
            assertThat(response.updateUrl()).isEqualTo("https://ios-update.example.com");
            assertThat(response.forceUpdate()).isTrue();
        }

        @Test
        @DisplayName("platform이 ANDROID(대소문자 무시)이면 Android 환경변수 값을 반환한다")
        void platform이_ANDROID이면_Android_환경변수_값을_반환한다() {
            VersionCheckResponse response = sut.checkVersion("android", "2.0.0");

            assertThat(response.minimumVersion()).isEqualTo("1.3.0");
            assertThat(response.updateUrl()).isEqualTo("https://android-update.example.com");
            assertThat(response.forceUpdate()).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"WINDOWS", "android2", "IOSX"})
        @DisplayName("platform이 IOS/ANDROID 외의 값이면 안전한 폴백 응답을 반환한다")
        void platform이_알수없는_값이면_안전한_폴백_응답을_반환한다(String platform) {
            VersionCheckResponse response = sut.checkVersion(platform, "1.0.0");

            assertThat(response.forceUpdate()).isFalse();
            assertThat(response.minimumVersion()).isNull();
            assertThat(response.updateUrl()).isNull();
        }

        @Test
        @DisplayName("version이 잘못된 형식이면 예외 없이 forceUpdate false를 반환한다")
        void version이_잘못된_형식이면_예외_없이_forceUpdate_false를_반환한다() {
            VersionCheckResponse response = sut.checkVersion("IOS", "abc");

            assertThat(response.forceUpdate()).isFalse();
        }
    }
}
