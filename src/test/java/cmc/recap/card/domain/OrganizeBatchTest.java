package cmc.recap.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizeBatchTest {

    private final User user = User.createByDevice("device-1", Platform.ANDROID);

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("totalCount가 0 이하이면 start() 시 예외를 던진다")
    void totalCount가_0_이하이면_start_시_예외를_던진다(int totalCount) {
        assertThatThrownBy(() -> OrganizeBatch.start(user, totalCount))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("start()로 생성하면 PROCESSING 상태로 시작한다")
    void start_하면_PROCESSING_상태로_시작한다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 3);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PROCESSING);
        assertThat(batch.getTotalCount()).isEqualTo(3);
        assertThat(batch.getSuccessCount()).isEqualTo(0);
        assertThat(batch.getFailCount()).isEqualTo(0);
        assertThat(batch.isAcknowledged()).isFalse();
    }

    @Test
    @DisplayName("모든 항목이 성공하면 COMPLETED 상태가 된다")
    void 모든_항목이_성공하면_COMPLETED_상태가_된다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 2);

        batch.recordSuccess();
        batch.recordSuccess();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("모든 항목이 실패하면 FAILED 상태가 된다")
    void 모든_항목이_실패하면_FAILED_상태가_된다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 2);

        batch.recordFailure();
        batch.recordFailure();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("성공과 실패가 섞이면 PARTIAL_FAILED 상태가 된다")
    void 성공과_실패가_섞이면_PARTIAL_FAILED_상태가_된다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 2);

        batch.recordSuccess();
        batch.recordFailure();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PARTIAL_FAILED);
    }

    @Test
    @DisplayName("아직 모든 항목이 처리되지 않았으면 PROCESSING 상태를 유지한다")
    void 아직_모든_항목이_처리되지_않았으면_PROCESSING_상태를_유지한다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 3);

        batch.recordSuccess();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PROCESSING);
    }

    @Test
    @DisplayName("PROCESSING 상태에서 cancel()하면 CANCELLED 상태가 된다")
    void PROCESSING_상태에서_cancel_하면_CANCELLED_상태가_된다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 3);

        batch.cancel();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
    }

    @Test
    @DisplayName("CANCELLED 상태에서 recordSuccess()가 호출돼도 상태가 되돌아가지 않는다")
    void CANCELLED_상태에서_recordSuccess_가_호출돼도_상태가_되돌아가지_않는다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 2);
        batch.cancel();

        batch.recordSuccess();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 완료된 배치를 cancel()해도 상태가 바뀌지 않는다")
    void 이미_완료된_배치를_cancel_해도_상태가_바뀌지_않는다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 1);
        batch.recordSuccess();

        batch.cancel();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("acknowledge()하면 acknowledged가 true가 된다")
    void acknowledge_하면_acknowledged가_true가_된다() {
        OrganizeBatch batch = OrganizeBatch.start(user, 1);

        batch.acknowledge();

        assertThat(batch.isAcknowledged()).isTrue();
    }
}
