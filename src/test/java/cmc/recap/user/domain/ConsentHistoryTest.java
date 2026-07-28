package cmc.recap.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsentHistoryTest {

    @Test
    @DisplayName("give()로 생성하면 GIVEN 이력이 만들어진다")
    void give로_생성하면_GIVEN_이력이_만들어진다() {
        User user = User.createByDevice("device-1", Platform.IOS);

        ConsentHistory history = ConsentHistory.give(user);

        assertThat(history.getUser()).isEqualTo(user);
        assertThat(history.getAction()).isEqualTo(ConsentAction.GIVEN);
    }

    @Test
    @DisplayName("withdraw()로 생성하면 WITHDRAWN 이력이 만들어진다")
    void withdraw로_생성하면_WITHDRAWN_이력이_만들어진다() {
        User user = User.createByDevice("device-1", Platform.IOS);

        ConsentHistory history = ConsentHistory.withdraw(user);

        assertThat(history.getUser()).isEqualTo(user);
        assertThat(history.getAction()).isEqualTo(ConsentAction.WITHDRAWN);
    }
}
