package cmc.recap.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cmc.recap.auth.repository.RefreshTokenRepository;
import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.report.repository.ReportRepository;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.dto.response.AccountInfoResponse;
import cmc.recap.user.dto.response.DataSummaryResponse;
import cmc.recap.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String BUCKET_NAME = "test-bucket";

    @Mock
    private UserRepository userRepository;
    @Mock
    private InfoCardRepository infoCardRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private S3Client s3Client;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, infoCardRepository, refreshTokenRepository, reportRepository, s3Client, BUCKET_NAME);
    }

    @Test
    @DisplayName("정상 탈퇴 시 InfoCard와 원본 S3 오브젝트, RefreshToken을 모두 삭제하고 유저를 익명화한다")
    void 정상_탈퇴_시_InfoCard와_원본_S3_오브젝트_RefreshToken을_모두_삭제하고_유저를_익명화한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        user.linkOauth("kakao", "oauth-1");
        user.updateFcmToken("fcm-token");
        String originalDeviceId = user.getDeviceId();
        InfoCard card = InfoCard.create(
                user, CardType.JOB, "title", "summary", "body", "captures/1/a.jpg", "extracted", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(infoCardRepository.findByUser(user)).willReturn(List.of(card));

        userService.withdraw(1L);

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captor.getValue().delete().objects())
                .extracting(ObjectIdentifier::key)
                .containsExactly("captures/1/a.jpg");
        verify(infoCardRepository).deleteAll(List.of(card));
        verify(refreshTokenRepository).deleteByUser(user);
        verify(reportRepository).deleteByUser(user);

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getDeviceId()).startsWith("WITHDRAWN-").isNotEqualTo(originalDeviceId);
        assertThat(user.getOauthProvider()).isNull();
        assertThat(user.getOauthId()).isNull();
        assertThat(user.getFcmToken()).isNull();
    }

    @Test
    @DisplayName("탈퇴 시 originalImageKey가 null인 카드는 S3 삭제 대상에서 제외하고 InfoCard는 정상 삭제한다")
    void 탈퇴_시_originalImageKey가_null인_카드는_S3_삭제_대상에서_제외하고_InfoCard는_정상_삭제한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        InfoCard expiredCard = InfoCard.create(
                user, CardType.JOB, "title", "summary", "body", "captures/1/a.jpg", "extracted", null);
        expiredCard.expireOriginalImage();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(infoCardRepository.findByUser(user)).willReturn(List.of(expiredCard));

        userService.withdraw(1L);

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
        verify(infoCardRepository).deleteAll(List.of(expiredCard));
    }

    @Test
    @DisplayName("이미 탈퇴한 유저가 재탈퇴를 요청하면 ALREADY_WITHDRAWN을 던진다")
    void 이미_탈퇴한_유저가_재탈퇴를_요청하면_ALREADY_WITHDRAWN을_던진다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        user.withdraw();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(infoCardRepository.findByUser(user)).willReturn(List.of());

        assertThatThrownBy(() -> userService.withdraw(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_WITHDRAWN);
    }

    @Nested
    @DisplayName("deleteAccountData")
    class DeleteAccountData {

        @Test
        @DisplayName("호출하면 InfoCard와 원본 S3 오브젝트를 삭제하되 RefreshToken 폐기와 계정 익명화는 하지 않는다")
        void 호출하면_InfoCard와_원본_S3_오브젝트를_삭제하되_RefreshToken_폐기와_계정_익명화는_하지_않는다() {
            User user = User.createByDevice("device-1", Platform.IOS);
            user.linkOauth("kakao", "oauth-1");
            String originalDeviceId = user.getDeviceId();
            InfoCard card = InfoCard.create(
                    user, CardType.JOB, "title", "summary", "body", "captures/1/a.jpg", "extracted", null);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(infoCardRepository.findByUser(user)).willReturn(List.of(card));

            userService.deleteAccountData(1L);

            ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
            verify(s3Client).deleteObjects(captor.capture());
            assertThat(captor.getValue().delete().objects())
                    .extracting(ObjectIdentifier::key)
                    .containsExactly("captures/1/a.jpg");
            verify(infoCardRepository).deleteAll(List.of(card));

            // 핵심 검증: 계정 유지/세션 유지 — RefreshToken 폐기·익명화가 절대 일어나면 안 된다
            verify(refreshTokenRepository, never()).deleteByUser(any());
            assertThat(user.isWithdrawn()).isFalse();
            assertThat(user.getDeviceId()).isEqualTo(originalDeviceId);
            assertThat(user.getOauthProvider()).isEqualTo("kakao");
            assertThat(user.getOauthId()).isEqualTo("oauth-1");
        }
    }

    @Nested
    @DisplayName("getAccountInfo")
    class GetAccountInfo {

        @Test
        @DisplayName("kakao로 가입한 유저를 조회하면 platform이 소문자 kakao와 가입일을 반환한다")
        void kakao로_가입한_유저를_조회하면_platform이_소문자_kakao와_가입일을_반환한다() {
            User user = User.createByDevice("device-1", Platform.IOS);
            user.linkOauth("KAKAO", "oauth-1");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            AccountInfoResponse response = userService.getAccountInfo(1L);

            assertThat(response.platform()).isEqualTo("kakao");
            assertThat(response.createdAt()).isEqualTo(user.getCreatedAt());
        }

        @Test
        @DisplayName("apple로 가입한 유저를 조회하면 platform이 소문자 apple과 가입일을 반환한다")
        void apple로_가입한_유저를_조회하면_platform이_소문자_apple과_가입일을_반환한다() {
            User user = User.createByDevice("device-2", Platform.IOS);
            user.linkOauth("APPLE", "oauth-2");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            AccountInfoResponse response = userService.getAccountInfo(1L);

            assertThat(response.platform()).isEqualTo("apple");
            assertThat(response.createdAt()).isEqualTo(user.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("getDataSummary")
    class GetDataSummary {

        @Test
        @DisplayName("호출하면 정리된 캡처 개수를 반환한다")
        void 호출하면_정리된_캡처_개수를_반환한다() {
            User user = User.createByDevice("device-1", Platform.IOS);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(infoCardRepository.countByUser(user)).willReturn(3L);

            DataSummaryResponse response = userService.getDataSummary(1L);

            assertThat(response.capturedCount()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("S3 배치 삭제 청크 분할")
    class S3DeleteChunking {

        @Test
        @DisplayName("삭제 대상 이미지 키가 1000개를 초과하면 deleteObjects를 여러 번 나눠 호출한다")
        void 삭제_대상_이미지_키가_1000개를_초과하면_deleteObjects를_여러_번_나눠_호출한다() {
            User user = User.createByDevice("device-1", Platform.IOS);
            List<InfoCard> cards = new ArrayList<>();
            for (int i = 0; i < 1500; i++) {
                cards.add(InfoCard.create(
                        user, CardType.JOB, "title", "summary", "body",
                        "captures/1/" + i + ".jpg", "extracted", null));
            }
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(infoCardRepository.findByUser(user)).willReturn(cards);

            userService.withdraw(1L);

            ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
            verify(s3Client, org.mockito.Mockito.times(2)).deleteObjects(captor.capture());
            List<DeleteObjectsRequest> requests = captor.getAllValues();
            assertThat(requests.get(0).delete().objects()).hasSize(1000);
            assertThat(requests.get(1).delete().objects()).hasSize(500);
        }
    }
}
