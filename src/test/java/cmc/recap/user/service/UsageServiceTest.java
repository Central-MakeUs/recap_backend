package cmc.recap.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.dto.response.UsageResponse;
import cmc.recap.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InfoCardRepository infoCardRepository;

    private UsageService usageService;
    private User user;

    @BeforeEach
    void setUp() {
        usageService = new UsageService(userRepository, infoCardRepository);
        user = User.createByDevice("device-1", Platform.IOS);
        given(userRepository.getReferenceById(1L)).willReturn(user);
    }

    @Nested
    @DisplayName("getUsage")
    class GetUsage {

        @Test
        @DisplayName("이번 달 InfoCard 개수를 정확히 세어 usedCount/remaining을 반환한다")
        void 이번_달_InfoCard_개수를_정확히_세어_usedCount_remaining을_반환한다() {
            given(infoCardRepository.countByUserAndCreatedAtAfter(eq(user), any(Instant.class))).willReturn(5L);

            UsageResponse response = usageService.getUsage(1L);

            assertThat(response.usedCount()).isEqualTo(5);
            assertThat(response.limit()).isEqualTo(20);
            assertThat(response.remaining()).isEqualTo(15);
        }

        @Test
        @DisplayName("monthStart는 이번 달 1일 00:00 UTC를 카운트 기준으로 사용한다")
        void monthStart는_이번_달_1일_00시_UTC를_카운트_기준으로_사용한다() {
            ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
            given(infoCardRepository.countByUserAndCreatedAtAfter(eq(user), sinceCaptor.capture())).willReturn(0L);

            usageService.getUsage(1L);

            Instant expectedMonthStart = LocalDate.now(ZoneOffset.UTC)
                    .withDayOfMonth(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
            assertThat(sinceCaptor.getValue()).isEqualTo(expectedMonthStart);
        }

        @Test
        @DisplayName("resetAt은 다음 달 1일 00:00 UTC를 반환한다")
        void resetAt은_다음_달_1일_00시_UTC를_반환한다() {
            given(infoCardRepository.countByUserAndCreatedAtAfter(eq(user), any(Instant.class))).willReturn(0L);

            UsageResponse response = usageService.getUsage(1L);

            Instant expectedResetAt = LocalDate.now(ZoneOffset.UTC)
                    .withDayOfMonth(1)
                    .plusMonths(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
            assertThat(response.resetAt()).isEqualTo(expectedResetAt);
        }
    }

    @Nested
    @DisplayName("checkLimit")
    class CheckLimit {

        @Test
        @DisplayName("사용량 + 요청 개수가 정확히 20이면(경계값) 예외를 던지지 않는다")
        void 사용량과_요청_개수의_합이_정확히_20이면_예외를_던지지_않는다() {
            given(infoCardRepository.countByUserAndCreatedAtAfter(eq(user), any(Instant.class))).willReturn(19L);

            assertThatCode(() -> usageService.checkLimit(1L, 1)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("사용량 + 요청 개수가 21이면(경계값) MONTHLY_USAGE_LIMIT_EXCEEDED를 던진다")
        void 사용량과_요청_개수의_합이_21이면_MONTHLY_USAGE_LIMIT_EXCEEDED를_던진다() {
            given(infoCardRepository.countByUserAndCreatedAtAfter(eq(user), any(Instant.class))).willReturn(20L);

            assertThatThrownBy(() -> usageService.checkLimit(1L, 1))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MONTHLY_USAGE_LIMIT_EXCEEDED);
        }
    }
}
