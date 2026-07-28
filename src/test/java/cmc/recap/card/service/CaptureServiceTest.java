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
import cmc.recap.report.domain.Report;
import cmc.recap.report.domain.ReportReason;
import cmc.recap.report.repository.ReportRepository;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@ExtendWith(MockitoExtension.class)
class CaptureServiceTest {

    private static final String BUCKET_NAME = "test-bucket";

    @Mock
    private ImagePresignedUrlProvider imagePresignedUrlProvider;
    @Mock
    private InfoCardRepository infoCardRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private S3Client s3Client;

    private CaptureService captureService;

    @BeforeEach
    void setUp() {
        captureService = new CaptureService(
                imagePresignedUrlProvider, infoCardRepository, reportRepository, userRepository, s3Client,
                BUCKET_NAME);
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
    @DisplayName("getDetail은 originalImageKey가 null이면 presigned URL 발급 없이 originalImageUrl을 null로 반환한다")
    void getDetail은_originalImageKey가_null이면_presigned_URL_발급_없이_originalImageUrl을_null로_반환한다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        card.expireOriginalImage();
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        CaptureDetailResponse response = captureService.getDetail(1L, 10L);

        assertThat(response.originalImageUrl()).isNull();
        verify(imagePresignedUrlProvider, never()).issueDownloadUrl(anyString());
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
    @DisplayName("updateBody는 다른 유저 소유면 NOT_FOUND를 던진다")
    void updateBody는_다른_유저_소유면_NOT_FOUND를_던진다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> captureService.updateBody(2L, 10L, "수정된 본문"))
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
    @DisplayName("delete는 originalImageKey가 null이면 S3 삭제 없이 InfoCard만 삭제한다")
    void delete는_originalImageKey가_null이면_S3_삭제_없이_InfoCard만_삭제한다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        card.expireOriginalImage();
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        captureService.delete(1L, 10L);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
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

    @Test
    @DisplayName("bulkDelete는 요청한 ID 전부 소유 시 S3 오브젝트와 InfoCard를 모두 삭제한다")
    void bulkDelete는_요청한_ID_전부_소유_시_S3_오브젝트와_InfoCard를_모두_삭제한다() {
        User owner = userWithId(1L);
        InfoCard card1 = cardWithId(10L, owner);
        InfoCard card2 = cardWithId(11L, owner);
        given(userRepository.getReferenceById(1L)).willReturn(owner);
        given(infoCardRepository.findByIdInAndUser(List.of(10L, 11L), owner))
                .willReturn(List.of(card1, card2));

        captureService.bulkDelete(1L, List.of(10L, 11L));

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captor.getValue().delete().objects())
                .extracting(ObjectIdentifier::key)
                .containsExactly("captures/1/a.jpg", "captures/1/a.jpg");
        verify(infoCardRepository).deleteAll(List.of(card1, card2));
    }

    @Test
    @DisplayName("bulkDelete는 다른 유저 소유/존재하지 않는 ID가 섞여도 조회된 소유 카드만 삭제한다")
    void bulkDelete는_다른_유저_소유_존재하지_않는_ID가_섞여도_조회된_소유_카드만_삭제한다() {
        User owner = userWithId(1L);
        InfoCard ownedCard = cardWithId(10L, owner);
        given(userRepository.getReferenceById(1L)).willReturn(owner);
        given(infoCardRepository.findByIdInAndUser(List.of(10L, 999L, 888L), owner))
                .willReturn(List.of(ownedCard));

        captureService.bulkDelete(1L, List.of(10L, 999L, 888L));

        verify(infoCardRepository).deleteAll(List.of(ownedCard));
        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        assertThat(captor.getValue().delete().objects())
                .extracting(ObjectIdentifier::key)
                .containsExactly("captures/1/a.jpg");
    }

    @Test
    @DisplayName("bulkDelete는 originalImageKey가 null인 카드를 S3 삭제 대상에서 제외하지만 DB에서는 삭제한다")
    void bulkDelete는_originalImageKey가_null인_카드를_S3_삭제_대상에서_제외하지만_DB에서는_삭제한다() {
        User owner = userWithId(1L);
        InfoCard expiredCard = cardWithId(10L, owner);
        expiredCard.expireOriginalImage();
        InfoCard normalCard = cardWithId(11L, owner);
        given(userRepository.getReferenceById(1L)).willReturn(owner);
        given(infoCardRepository.findByIdInAndUser(List.of(10L, 11L), owner))
                .willReturn(List.of(expiredCard, normalCard));

        captureService.bulkDelete(1L, List.of(10L, 11L));

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        assertThat(captor.getValue().delete().objects())
                .extracting(ObjectIdentifier::key)
                .containsExactly("captures/1/a.jpg");
        verify(infoCardRepository).deleteAll(List.of(expiredCard, normalCard));
    }

    @Test
    @DisplayName("bulkDelete는 captureIds가 비어있으면 INVALID_INPUT을 던지고 아무것도 조회/삭제하지 않는다")
    void bulkDelete는_captureIds가_비어있으면_INVALID_INPUT을_던지고_아무것도_조회_삭제하지_않는다() {
        assertThatThrownBy(() -> captureService.bulkDelete(1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(userRepository, never()).getReferenceById(any());
        verify(infoCardRepository, never()).findByIdInAndUser(any(), any());
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("bulkDelete는 1000개를 초과하면 S3 삭제 요청을 여러 번 청크로 나눠 호출한다")
    void bulkDelete는_1000개를_초과하면_S3_삭제_요청을_여러_번_청크로_나눠_호출한다() {
        User owner = userWithId(1L);
        List<Long> captureIds = IntStream.rangeClosed(1, 1500).mapToObj(Long::valueOf).toList();
        List<InfoCard> cards = captureIds.stream()
                .map(id -> cardWithId(id, owner))
                .toList();
        given(userRepository.getReferenceById(1L)).willReturn(owner);
        given(infoCardRepository.findByIdInAndUser(captureIds, owner)).willReturn(cards);

        captureService.bulkDelete(1L, captureIds);

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, times(2)).deleteObjects(captor.capture());
        List<DeleteObjectsRequest> requests = captor.getAllValues();
        assertThat(requests.get(0).delete().objects()).hasSize(1000);
        assertThat(requests.get(1).delete().objects()).hasSize(500);
        verify(infoCardRepository).deleteAll(cards);
    }

    @Test
    @DisplayName("report는 신고 시점 title/summary/cardType 스냅샷으로 Report를 저장한다")
    void report는_신고_시점_title_summary_cardType_스냅샷으로_Report를_저장한다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));
        given(reportRepository.existsByUserAndCaptureId(owner, 10L)).willReturn(false);

        captureService.report(1L, 10L, ReportReason.INACCURATE_CONTENT, "가격 정보가 실제와 달라요");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(owner);
        assertThat(saved.getCaptureId()).isEqualTo(10L);
        assertThat(saved.getCardType()).isEqualTo(CardType.JOB);
        assertThat(saved.getTitle()).isEqualTo("title");
        assertThat(saved.getSummary()).isEqualTo("summary");
        assertThat(saved.getReason()).isEqualTo(ReportReason.INACCURATE_CONTENT);
        assertThat(saved.getDetail()).isEqualTo("가격 정보가 실제와 달라요");
    }

    @Test
    @DisplayName("report는 detail 없이 호출해도 정상 저장한다")
    void report는_detail_없이_호출해도_정상_저장한다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));
        given(reportRepository.existsByUserAndCaptureId(owner, 10L)).willReturn(false);

        captureService.report(1L, 10L, ReportReason.INACCURATE_CONTENT, null);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    @DisplayName("report는 detail이 200자를 초과하면 INVALID_INPUT을 던진다")
    void report는_detail이_200자를_초과하면_INVALID_INPUT을_던진다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));
        given(reportRepository.existsByUserAndCaptureId(owner, 10L)).willReturn(false);
        String tooLong = "a".repeat(201);

        assertThatThrownBy(() -> captureService.report(1L, 10L, ReportReason.INACCURATE_CONTENT, tooLong))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("report는 이미 신고한 카드면 ALREADY_REPORTED를 던진다")
    void report는_이미_신고한_카드면_ALREADY_REPORTED를_던진다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));
        given(reportRepository.existsByUserAndCaptureId(owner, 10L)).willReturn(true);

        assertThatThrownBy(() -> captureService.report(1L, 10L, ReportReason.INACCURATE_CONTENT, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_REPORTED);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("report는 다른 유저 소유면 NOT_FOUND를 던진다")
    void report는_다른_유저_소유면_NOT_FOUND를_던진다() {
        User owner = userWithId(1L);
        InfoCard card = cardWithId(10L, owner);
        given(infoCardRepository.findById(10L)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> captureService.report(2L, 10L, ReportReason.INACCURATE_CONTENT, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(reportRepository, never()).save(any());
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
