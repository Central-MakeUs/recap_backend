package cmc.recap.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cmc.recap.card.domain.BatchStatus;
import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.domain.OrganizeBatch;
import cmc.recap.card.dto.response.OrganizeResponse;
import cmc.recap.card.image.ImageAnalysisResult;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.card.repository.OrganizeBatchRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
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
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@ExtendWith(MockitoExtension.class)
class OrganizeServiceTest {

    private static final String BUCKET_NAME = "test-bucket";

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizeBatchRepository organizeBatchRepository;
    @Mock
    private InfoCardRepository infoCardRepository;
    @Mock
    private ImageAnalysisTaskRunner imageAnalysisTaskRunner;
    @Mock
    private S3Client s3Client;

    private OrganizeService organizeService;

    @BeforeEach
    void setUp() {
        organizeService = new OrganizeService(
                userRepository, organizeBatchRepository, infoCardRepository, imageAnalysisTaskRunner,
                s3Client, BUCKET_NAME);
    }

    @Test
    @DisplayName("title이 30자 이하이면 그대로 반환한다")
    void title이_30자_이하이면_그대로_반환한다() {
        String title = "가".repeat(30);

        String result = organizeService.normalizeTitle(title);

        assertThat(result).isEqualTo(title);
    }

    @Test
    @DisplayName("title이 30자를 초과하면 29자로 자르고 말줄임표를 붙인다")
    void title이_30자를_초과하면_29자로_자르고_말줄임표를_붙인다() {
        String title = "가".repeat(35);

        String result = organizeService.normalizeTitle(title);

        assertThat(result).isEqualTo("가".repeat(29) + "…");
        assertThat(result.codePointCount(0, result.length())).isEqualTo(30);
    }

    @Test
    @DisplayName("title에 이모지가 포함돼 30자를 초과하면 서로게이트 쌍을 깨지 않고 자른다")
    void title에_이모지가_포함돼_30자를_초과하면_서로게이트_쌍을_깨지_않고_자른다() {
        String title = "가".repeat(29) + "😀" + "나";

        String result = organizeService.normalizeTitle(title);

        assertThat(result).isEqualTo("가".repeat(29) + "…");
        assertThat(result.codePointCount(0, result.length())).isEqualTo(30);
    }

    @Test
    @DisplayName("summary가 80자 이하이면 그대로 반환한다")
    void summary가_80자_이하이면_그대로_반환한다() {
        String summary = "가".repeat(80);

        String result = organizeService.normalizeSummary(summary);

        assertThat(result).isEqualTo(summary);
    }

    @Test
    @DisplayName("summary가 80자를 초과하면 79자로 자르고 말줄임표를 붙인다")
    void summary가_80자를_초과하면_79자로_자르고_말줄임표를_붙인다() {
        String summary = "가".repeat(85);

        String result = organizeService.normalizeSummary(summary);

        assertThat(result).isEqualTo("가".repeat(79) + "…");
        assertThat(result.codePointCount(0, result.length())).isEqualTo(80);
    }

    @Test
    @DisplayName("imageKeys가 20장을 초과하면 INVALID_INPUT을 던진다")
    void imageKeys가_20장을_초과하면_INVALID_INPUT을_던진다() {
        List<String> imageKeys = IntStream.range(0, 21).mapToObj(i -> "key-" + i).toList();

        assertThatThrownBy(() -> organizeService.organize(1L, imageKeys))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("imageKeys에 다른 유저의 objectKey가 섞여 있으면 INVALID_INPUT을 던지고 배치를 만들지 않는다")
    void imageKeys에_다른_유저의_objectKey가_섞여_있으면_INVALID_INPUT을_던지고_배치를_만들지_않는다() {
        List<String> imageKeys = List.of("captures/1/a.jpg", "captures/2/b.jpg");

        assertThatThrownBy(() -> organizeService.organize(1L, imageKeys))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(organizeBatchRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 PROCESSING 배치가 있으면 ORGANIZE_IN_PROGRESS를 던진다")
    void 이미_PROCESSING_배치가_있으면_ORGANIZE_IN_PROGRESS를_던진다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(organizeBatchRepository.existsByUserAndStatus(user, BatchStatus.PROCESSING)).willReturn(true);

        assertThatThrownBy(() -> organizeService.organize(1L, List.of("captures/1/a.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORGANIZE_IN_PROGRESS);

        verify(organizeBatchRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 요청이면 배치를 생성하고 이미지별로 비동기 분석을 디스패치한다")
    void 정상_요청이면_배치를_생성하고_이미지별로_비동기_분석을_디스패치한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(organizeBatchRepository.existsByUserAndStatus(user, BatchStatus.PROCESSING)).willReturn(false);
        given(organizeBatchRepository.save(any(OrganizeBatch.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        OrganizeResponse response = organizeService.organize(1L, List.of("captures/1/a.jpg", "captures/1/b.jpg"));

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(BatchStatus.PROCESSING);
        verify(imageAnalysisTaskRunner).analyzeAndSave(response.batchId(), "captures/1/a.jpg");
        verify(imageAnalysisTaskRunner).analyzeAndSave(response.batchId(), "captures/1/b.jpg");
    }

    @Test
    @DisplayName("completeImage는 배치를 락 조회해서 InfoCard를 저장하고 성공을 기록한다")
    void completeImage는_배치를_락_조회해서_InfoCard를_저장하고_성공을_기록한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        OrganizeBatch batch = OrganizeBatch.start(user, 1);
        given(organizeBatchRepository.findByIdForUpdate(1L)).willReturn(Optional.of(batch));
        ImageAnalysisResult result = new ImageAnalysisResult(
                CardType.JOB, "title", "summary", "body", "extracted");

        organizeService.completeImage(1L, "captures/1/a.jpg", result);

        assertThat(batch.getSuccessCount()).isEqualTo(1);
        verify(infoCardRepository).save(any());
    }

    @Test
    @DisplayName("completeImage는 배치가 CANCELLED면 InfoCard를 만들지 않고 그대로 리턴한다")
    void completeImage는_배치가_CANCELLED면_InfoCard를_만들지_않고_그대로_리턴한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        OrganizeBatch batch = OrganizeBatch.start(user, 2);
        batch.cancel();
        given(organizeBatchRepository.findByIdForUpdate(1L)).willReturn(Optional.of(batch));
        ImageAnalysisResult result = new ImageAnalysisResult(
                CardType.JOB, "title", "summary", "body", "extracted");

        organizeService.completeImage(1L, "captures/1/a.jpg", result);

        verify(infoCardRepository, never()).save(any());
        assertThat(batch.getSuccessCount()).isZero();
    }

    @Test
    @DisplayName("completeImage는 존재하지 않는 배치면 NOT_FOUND를 던진다")
    void completeImage는_존재하지_않는_배치면_NOT_FOUND를_던진다() {
        given(organizeBatchRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());
        ImageAnalysisResult result = new ImageAnalysisResult(
                CardType.JOB, "title", "summary", "body", "extracted");

        assertThatThrownBy(() -> organizeService.completeImage(1L, "captures/1/a.jpg", result))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("failImage는 배치를 락 조회해서 실패를 기록한다")
    void failImage는_배치를_락_조회해서_실패를_기록한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        OrganizeBatch batch = OrganizeBatch.start(user, 1);
        given(organizeBatchRepository.findByIdForUpdate(1L)).willReturn(Optional.of(batch));

        organizeService.failImage(1L);

        assertThat(batch.getFailCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("failImage는 배치가 CANCELLED면 실패를 기록하지 않고 그대로 리턴한다")
    void failImage는_배치가_CANCELLED면_실패를_기록하지_않고_그대로_리턴한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        OrganizeBatch batch = OrganizeBatch.start(user, 2);
        batch.cancel();
        given(organizeBatchRepository.findByIdForUpdate(1L)).willReturn(Optional.of(batch));

        organizeService.failImage(1L);

        assertThat(batch.getFailCount()).isZero();
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
    }

    @Test
    @DisplayName("getStatus는 배치 소유자면 상태를 반환한다")
    void getStatus는_배치_소유자면_상태를_반환한다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 3);
        batch.recordSuccess();
        given(organizeBatchRepository.findById(10L)).willReturn(Optional.of(batch));

        var response = organizeService.getStatus(1L, 10L);

        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(BatchStatus.PROCESSING);
    }

    @Test
    @DisplayName("getStatus는 배치 소유자가 아니면 NOT_FOUND를 던진다")
    void getStatus는_배치_소유자가_아니면_NOT_FOUND를_던진다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 1);
        given(organizeBatchRepository.findById(10L)).willReturn(Optional.of(batch));

        assertThatThrownBy(() -> organizeService.getStatus(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("cancel은 배치 소유자면 취소하고, 이미 생성된 InfoCard와 원본 S3 오브젝트를 함께 삭제한다")
    void cancel은_배치_소유자면_취소하고_이미_생성된_InfoCard와_원본_S3_오브젝트를_함께_삭제한다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 3);
        batch.recordSuccess();
        InfoCard card = InfoCard.create(
                owner, CardType.JOB, "title", "summary", "body", "captures/1/a.jpg", "extracted", batch);
        given(organizeBatchRepository.findById(10L)).willReturn(Optional.of(batch));
        given(organizeBatchRepository.findByIdForUpdate(10L)).willReturn(Optional.of(batch));
        given(infoCardRepository.findByBatch(batch)).willReturn(List.of(card));

        organizeService.cancel(1L, 10L);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
        assertThat(batch.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captor.getValue().delete().objects())
                .extracting(ObjectIdentifier::key)
                .containsExactly("captures/1/a.jpg");
        verify(infoCardRepository).deleteAll(List.of(card));
    }

    @Test
    @DisplayName("cancel은 이미 PROCESSING이 아닌 배치면 InfoCard를 삭제하지 않는다")
    void cancel은_이미_PROCESSING이_아닌_배치면_InfoCard를_삭제하지_않는다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 1);
        batch.recordSuccess();
        given(organizeBatchRepository.findById(10L)).willReturn(Optional.of(batch));
        given(organizeBatchRepository.findByIdForUpdate(10L)).willReturn(Optional.of(batch));

        organizeService.cancel(1L, 10L);

        verify(infoCardRepository, never()).findByBatch(any());
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("cancel은 배치 소유자가 아니면 NOT_FOUND를 던지고 취소하지 않는다")
    void cancel은_배치_소유자가_아니면_NOT_FOUND를_던지고_취소하지_않는다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 1);
        given(organizeBatchRepository.findById(10L)).willReturn(Optional.of(batch));

        assertThatThrownBy(() -> organizeService.cancel(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PROCESSING);
        verify(organizeBatchRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("getPendingResult는 확인하지 않은 완료 배치가 있으면 반환한다")
    void getPendingResult는_확인하지_않은_완료_배치가_있으면_반환한다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 1);
        batch.recordSuccess();
        given(userRepository.getReferenceById(1L)).willReturn(owner);
        given(organizeBatchRepository.findFirstByUserAndAcknowledgedFalseAndStatusInOrderByCreatedAtDesc(
                eq(owner), any())).willReturn(Optional.of(batch));

        var response = organizeService.getPendingResult(1L);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(response.successCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getPendingResult는 확인할 배치가 없으면 null을 반환한다")
    void getPendingResult는_확인할_배치가_없으면_null을_반환한다() {
        User owner = userWithId(1L);
        given(userRepository.getReferenceById(1L)).willReturn(owner);
        given(organizeBatchRepository.findFirstByUserAndAcknowledgedFalseAndStatusInOrderByCreatedAtDesc(
                eq(owner), any())).willReturn(Optional.empty());

        var response = organizeService.getPendingResult(1L);

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("ack는 배치 소유자면 확인 처리한다")
    void ack는_배치_소유자면_확인_처리한다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 1);
        given(organizeBatchRepository.findById(10L)).willReturn(Optional.of(batch));

        organizeService.ack(1L, 10L);

        assertThat(batch.isAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("ack는 배치 소유자가 아니면 NOT_FOUND를 던지고 확인 처리하지 않는다")
    void ack는_배치_소유자가_아니면_NOT_FOUND를_던지고_확인_처리하지_않는다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 1);
        given(organizeBatchRepository.findById(10L)).willReturn(Optional.of(batch));

        assertThatThrownBy(() -> organizeService.ack(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(batch.isAcknowledged()).isFalse();
    }

    @Test
    @DisplayName("ack 이후 pending-result를 재조회하면 그 배치는 더 이상 조회되지 않는다")
    void ack_이후_pending_result를_재조회하면_그_배치는_더_이상_조회되지_않는다() {
        User owner = userWithId(1L);
        OrganizeBatch batch = OrganizeBatch.start(owner, 1);
        batch.recordSuccess();
        given(organizeBatchRepository.findById(10L)).willReturn(Optional.of(batch));

        organizeService.ack(1L, 10L);

        assertThat(batch.isAcknowledged()).isTrue();

        given(userRepository.getReferenceById(1L)).willReturn(owner);
        given(organizeBatchRepository.findFirstByUserAndAcknowledgedFalseAndStatusInOrderByCreatedAtDesc(
                eq(owner), any())).willReturn(Optional.empty());

        var response = organizeService.getPendingResult(1L);

        assertThat(response).isNull();
    }

    private User userWithId(Long id) {
        User user = User.createByDevice("device-" + id, Platform.IOS);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
