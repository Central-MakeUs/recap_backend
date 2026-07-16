package cmc.recap.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.dto.response.CaptureDetailResponse;
import cmc.recap.card.dto.response.UploadUrlsResponse;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.image.PresignedUploadInfo;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@ExtendWith(MockitoExtension.class)
class CaptureServiceTest {

    private static final String BUCKET_NAME = "test-bucket";

    @Mock
    private ImagePresignedUrlProvider imagePresignedUrlProvider;
    @Mock
    private InfoCardRepository infoCardRepository;
    @Mock
    private S3Client s3Client;

    private CaptureService captureService;

    @BeforeEach
    void setUp() {
        captureService = new CaptureService(imagePresignedUrlProvider, infoCardRepository, s3Client, BUCKET_NAME);
    }

    @Test
    @DisplayName("count만큼 objectKey와 uploadUrl 쌍을 발급한다")
    void count만큼_objectKey와_uploadUrl_쌍을_발급한다() {
        given(imagePresignedUrlProvider.issueUploadUrl(anyString()))
                .willReturn(new PresignedUploadInfo("https://s3.example.com/put", Instant.now()));

        UploadUrlsResponse response = captureService.issueUploadUrls(1L, 3);

        assertThat(response.uploads()).hasSize(3);
        response.uploads().forEach(item -> {
            assertThat(item.imageKey()).startsWith("captures/1/");
            assertThat(item.uploadUrl()).isEqualTo("https://s3.example.com/put");
        });
        verify(imagePresignedUrlProvider, times(3)).issueUploadUrl(any());
    }

    @Test
    @DisplayName("getDetail은 소유자면 원본 이미지 URL을 발급해 상세 응답을 반환한다")
    void getDetail은_소유자면_원본_이미지_URL을_발급해_상세_응답을_반환한다() throws Exception {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));
        given(imagePresignedUrlProvider.issueDownloadUrl("captures/1/a.jpg"))
                .willReturn(URI.create("https://s3.example.com/original").toURL());

        CaptureDetailResponse response = captureService.getDetail(1L, 10L);

        assertThat(response.captureId()).isEqualTo(10L);
        assertThat(response.originalImageUrl()).isEqualTo("https://s3.example.com/original");
    }

    @Test
    @DisplayName("getDetail은 다른 유저 소유면 NOT_FOUND를 던진다")
    void getDetail은_다른_유저_소유면_NOT_FOUND를_던진다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> captureService.getDetail(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("updateFavorite는 true로 요청하면 즐겨찾기로 반영한다")
    void updateFavorite는_true로_요청하면_즐겨찾기로_반영한다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        captureService.updateFavorite(1L, 10L, true);

        assertThat(card.isFavorite()).isTrue();
    }

    @Test
    @DisplayName("updateFavorite는 false로 요청하면 즐겨찾기 해제로 반영한다")
    void updateFavorite는_false로_요청하면_즐겨찾기_해제로_반영한다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        card.markFavorite(true);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        captureService.updateFavorite(1L, 10L, false);

        assertThat(card.isFavorite()).isFalse();
    }

    @Test
    @DisplayName("updateFavorite는 같은 값으로 연속 호출해도 결과가 동일하다(멱등)")
    void updateFavorite는_같은_값으로_연속_호출해도_결과가_동일하다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        captureService.updateFavorite(1L, 10L, true);
        captureService.updateFavorite(1L, 10L, true);

        assertThat(card.isFavorite()).isTrue();
    }

    @Test
    @DisplayName("updateFavorite는 다른 유저 소유면 NOT_FOUND를 던진다")
    void updateFavorite는_다른_유저_소유면_NOT_FOUND를_던진다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> captureService.updateFavorite(2L, 10L, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("delete는 소유자면 S3 원본 이미지와 InfoCard를 모두 삭제한다")
    void delete는_소유자면_S3_원본_이미지와_InfoCard를_모두_삭제한다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        captureService.delete(1L, 10L);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captor.getValue().key()).isEqualTo("captures/1/a.jpg");
        verify(infoCardRepository).delete(card);
    }

    @Test
    @DisplayName("delete는 다른 유저 소유면 NOT_FOUND를 던지고 삭제를 진행하지 않는다")
    void delete는_다른_유저_소유면_NOT_FOUND를_던지고_삭제를_진행하지_않는다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> captureService.delete(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(infoCardRepository, never()).delete(any());
    }

    private User userWithId(Long id) {
        User user = User.createByDevice("device-" + id, Platform.IOS);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InfoCard cardWithId(Long id, User owner) {
        InfoCard card = InfoCard.create(
                owner, CardType.JOB, "title", "summary", "body", "captures/1/a.jpg", "extracted", null);
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }
}
