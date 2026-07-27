package cmc.recap.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import cmc.recap.auth.repository.RefreshTokenRepository;
import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    private S3Client s3Client;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, infoCardRepository, refreshTokenRepository, s3Client, BUCKET_NAME);
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

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getDeviceId()).startsWith("WITHDRAWN-").isNotEqualTo(originalDeviceId);
        assertThat(user.getOauthProvider()).isNull();
        assertThat(user.getOauthId()).isNull();
        assertThat(user.getFcmToken()).isNull();
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
}
