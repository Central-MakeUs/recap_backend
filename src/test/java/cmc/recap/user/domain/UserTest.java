package cmc.recap.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    @DisplayName("deviceId 없이 생성하면 예외를 던진다")
    void deviceId_없이_생성하면_예외를_던진다() {
        assertThatThrownBy(() -> User.createByDevice(null, Platform.IOS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> User.createByDevice("  ", Platform.IOS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("deviceId로 생성하면 익명 유저가 만들어진다")
    void deviceId로_생성하면_익명_유저가_만들어진다() {
        User user = User.createByDevice("device-1", Platform.IOS);

        assertThat(user.getDeviceId()).isEqualTo("device-1");
        assertThat(user.getPlatform()).isEqualTo(Platform.IOS);
    }

    @Test
    @DisplayName("이미 OAuth가 연결된 유저를 다시 연결하면 예외를 던진다")
    void 이미_연결된_OAuth를_재연결하면_예외를_던진다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        user.linkOauth("kakao", "oauth-1");

        assertThatThrownBy(() -> user.linkOauth("apple", "oauth-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_LINKED_OAUTH);
    }
}
