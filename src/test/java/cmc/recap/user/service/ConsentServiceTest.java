package cmc.recap.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import cmc.recap.user.domain.ConsentHistory;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.dto.response.ConsentStatusResponse;
import cmc.recap.user.repository.ConsentHistoryRepository;
import cmc.recap.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ConsentHistoryRepository consentHistoryRepository;

    private ConsentService consentService;
    private User user;

    @BeforeEach
    void setUp() {
        consentService = new ConsentService(userRepository, consentHistoryRepository);
        user = User.createByDevice("device-1", Platform.IOS);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("getStatus")
    class GetStatus {

        @Test
        @DisplayName("이력이 없으면 미동의 상태를 반환한다")
        void 이력이_없으면_미동의_상태를_반환한다() {
            given(consentHistoryRepository.findFirstByUserOrderByCreatedAtDesc(user)).willReturn(Optional.empty());

            ConsentStatusResponse response = consentService.getStatus(1L);

            assertThat(response.consented()).isFalse();
            assertThat(response.consentedAt()).isNull();
        }

        @Test
        @DisplayName("최신 이력이 GIVEN이면 동의 상태와 동의 시각을 반환한다")
        void 최신_이력이_GIVEN이면_동의_상태와_동의_시각을_반환한다() {
            ConsentHistory history = ConsentHistory.give(user);
            Instant givenAt = Instant.parse("2026-07-29T00:00:00Z");
            ReflectionTestUtils.setField(history, "createdAt", givenAt);
            given(consentHistoryRepository.findFirstByUserOrderByCreatedAtDesc(user))
                    .willReturn(Optional.of(history));

            ConsentStatusResponse response = consentService.getStatus(1L);

            assertThat(response.consented()).isTrue();
            assertThat(response.consentedAt()).isEqualTo(givenAt);
        }

        @Test
        @DisplayName("최신 이력이 WITHDRAWN이면 미동의 상태를 반환한다")
        void 최신_이력이_WITHDRAWN이면_미동의_상태를_반환한다() {
            ConsentHistory history = ConsentHistory.withdraw(user);
            given(consentHistoryRepository.findFirstByUserOrderByCreatedAtDesc(user))
                    .willReturn(Optional.of(history));

            ConsentStatusResponse response = consentService.getStatus(1L);

            assertThat(response.consented()).isFalse();
            assertThat(response.consentedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("hasActiveConsent")
    class HasActiveConsent {

        @Test
        @DisplayName("이력이 없으면 false를 반환한다")
        void 이력이_없으면_false를_반환한다() {
            given(consentHistoryRepository.findFirstByUserOrderByCreatedAtDesc(user)).willReturn(Optional.empty());

            boolean result = consentService.hasActiveConsent(1L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("최신 이력이 GIVEN이면 true를 반환한다")
        void 최신_이력이_GIVEN이면_true를_반환한다() {
            given(consentHistoryRepository.findFirstByUserOrderByCreatedAtDesc(user))
                    .willReturn(Optional.of(ConsentHistory.give(user)));

            boolean result = consentService.hasActiveConsent(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("최신 이력이 WITHDRAWN이면 false를 반환한다")
        void 최신_이력이_WITHDRAWN이면_false를_반환한다() {
            given(consentHistoryRepository.findFirstByUserOrderByCreatedAtDesc(user))
                    .willReturn(Optional.of(ConsentHistory.withdraw(user)));

            boolean result = consentService.hasActiveConsent(1L);

            assertThat(result).isFalse();
        }
    }
}
