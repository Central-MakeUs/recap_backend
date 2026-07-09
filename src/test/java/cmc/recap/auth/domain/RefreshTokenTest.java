package cmc.recap.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private final User user = User.createByDevice("device-1", Platform.IOS);

    @Test
    @DisplayName("발급하면 폐기되지 않은 상태로 만들어진다")
    void 발급하면_폐기되지_않은_상태로_만들어진다() {
        RefreshToken token = RefreshToken.issue(user, "hash", Instant.now().plus(14, ChronoUnit.DAYS));

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.getTokenHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("만료 전이고 폐기되지 않았으면 사용 가능하다")
    void 만료_전이고_폐기되지_않았으면_사용_가능하다() {
        RefreshToken token = RefreshToken.issue(user, "hash", Instant.now().plus(14, ChronoUnit.DAYS));

        assertThat(token.isUsable()).isTrue();
    }

    @Test
    @DisplayName("만료 시각이 지나면 사용 불가능하다")
    void 만료_시각이_지나면_사용_불가능하다() {
        RefreshToken token = RefreshToken.issue(user, "hash", Instant.now().minus(1, ChronoUnit.SECONDS));

        assertThat(token.isUsable()).isFalse();
    }

    @Test
    @DisplayName("폐기하면 사용 불가능하다")
    void 폐기하면_사용_불가능하다() {
        RefreshToken token = RefreshToken.issue(user, "hash", Instant.now().plus(14, ChronoUnit.DAYS));

        token.revoke();

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isUsable()).isFalse();
    }
}
