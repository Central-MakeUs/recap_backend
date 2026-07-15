package cmc.recap.card.service;

import cmc.recap.card.domain.BatchStatus;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.domain.OrganizeBatch;
import cmc.recap.card.dto.response.OrganizeResponse;
import cmc.recap.card.dto.response.OrganizeStatusResponse;
import cmc.recap.card.dto.response.PendingResultResponse;
import cmc.recap.card.image.CaptureObjectKeyGenerator;
import cmc.recap.card.image.ImageAnalysisResult;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.card.repository.OrganizeBatchRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

/**
 * 정리(Organize) 처리 서비스. 배치 생성/이미지별 비동기 분석 결과 반영과
 * {@link InfoCard#create}에 넘기기 전 title/summary 정규화(길이 제약)를
 * 담당한다.
 */
@Service
public class OrganizeService {

    private static final String TRUNCATION_SUFFIX = "…";
    private static final int MAX_IMAGE_COUNT = 20;
    private static final List<BatchStatus> PENDING_RESULT_STATUSES =
            List.of(BatchStatus.COMPLETED, BatchStatus.PARTIAL_FAILED, BatchStatus.FAILED);

    private final UserRepository userRepository;
    private final OrganizeBatchRepository organizeBatchRepository;
    private final InfoCardRepository infoCardRepository;
    private final ImageAnalysisTaskRunner imageAnalysisTaskRunner;
    private final S3Client s3Client;
    private final String bucketName;

    public OrganizeService(
            UserRepository userRepository,
            OrganizeBatchRepository organizeBatchRepository,
            InfoCardRepository infoCardRepository,
            ImageAnalysisTaskRunner imageAnalysisTaskRunner,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.userRepository = userRepository;
        this.organizeBatchRepository = organizeBatchRepository;
        this.infoCardRepository = infoCardRepository;
        this.imageAnalysisTaskRunner = imageAnalysisTaskRunner;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public OrganizeResponse organize(Long userId, List<String> imageKeys) {
        validateImageKeys(userId, imageKeys);
        User user = userRepository.getReferenceById(userId);
        if (organizeBatchRepository.existsByUserAndStatus(user, BatchStatus.PROCESSING)) {
            throw new BusinessException(ErrorCode.ORGANIZE_IN_PROGRESS);
        }
        OrganizeBatch batch = organizeBatchRepository.save(OrganizeBatch.start(user, imageKeys.size()));
        imageKeys.forEach(imageKey -> imageAnalysisTaskRunner.analyzeAndSave(batch.getId(), imageKey));
        return OrganizeResponse.of(batch.getId(), batch.getTotalCount(), batch.getStatus());
    }

    @Transactional
    public void completeImage(Long batchId, String imageKey, ImageAnalysisResult result) {
        OrganizeBatch batch = getBatchForUpdate(batchId);
        if (batch.getStatus() == BatchStatus.CANCELLED) {
            return;
        }
        String title = normalizeTitle(result.title());
        String summary = normalizeSummary(result.summary());
        InfoCard card = InfoCard.create(
                batch.getUser(), result.type(), title, summary, result.body(), imageKey, result.extractedText(), batch);
        infoCardRepository.save(card);
        batch.recordSuccess();
    }

    @Transactional
    public void failImage(Long batchId) {
        OrganizeBatch batch = getBatchForUpdate(batchId);
        if (batch.getStatus() == BatchStatus.CANCELLED) {
            return;
        }
        batch.recordFailure();
    }

    public OrganizeStatusResponse getStatus(Long userId, Long batchId) {
        OrganizeBatch batch = getOwnedBatch(userId, batchId);
        return OrganizeStatusResponse.of(
                batch.getId(), batch.getStatus(), batch.getTotalCount(), batch.getSuccessCount(), batch.getFailCount());
    }

    @Transactional
    public void cancel(Long userId, Long batchId) {
        OrganizeBatch batch = getOwnedBatchForUpdate(userId, batchId);
        boolean wasProcessing = batch.getStatus() == BatchStatus.PROCESSING;
        batch.cancel();
        if (wasProcessing) {
            deleteCards(batch);
        }
    }

    public PendingResultResponse getPendingResult(Long userId) {
        User user = userRepository.getReferenceById(userId);
        return organizeBatchRepository
                .findFirstByUserAndAcknowledgedFalseAndStatusInOrderByCreatedAtDesc(user, PENDING_RESULT_STATUSES)
                .map(batch -> PendingResultResponse.of(
                        batch.getId(), batch.getStatus(), batch.getSuccessCount(), batch.getFailCount()))
                .orElse(null);
    }

    @Transactional
    public void ack(Long userId, Long batchId) {
        getOwnedBatch(userId, batchId).acknowledge();
    }

    public String normalizeTitle(String title) {
        return truncateByCodePoint(title, InfoCard.TITLE_MAX_LENGTH);
    }

    public String normalizeSummary(String summary) {
        return truncateByCodePoint(summary, InfoCard.SUMMARY_MAX_LENGTH);
    }

    private OrganizeBatch getBatchForUpdate(Long batchId) {
        return organizeBatchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private OrganizeBatch getOwnedBatch(Long userId, Long batchId) {
        OrganizeBatch batch = organizeBatchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateOwner(userId, batch);
        return batch;
    }

    private OrganizeBatch getOwnedBatchForUpdate(Long userId, Long batchId) {
        OrganizeBatch batch = organizeBatchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateOwner(userId, batch);
        return getBatchForUpdate(batchId);
    }

    private void deleteCards(OrganizeBatch batch) {
        List<InfoCard> cards = infoCardRepository.findByBatch(batch);
        if (cards.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> objectIds = cards.stream()
                .map(card -> ObjectIdentifier.builder().key(card.getOriginalImageKey()).build())
                .toList();
        try {
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objectIds).build())
                    .build());
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
        infoCardRepository.deleteAll(cards);
    }

    private void validateOwner(Long userId, OrganizeBatch batch) {
        if (!userId.equals(batch.getUser().getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private void validateImageKeys(Long userId, List<String> imageKeys) {
        if (imageKeys.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "imageKeys는 " + MAX_IMAGE_COUNT + "장을 초과할 수 없습니다.");
        }
        boolean hasOtherUsersKey = imageKeys.stream()
                .anyMatch(imageKey -> !CaptureObjectKeyGenerator.belongsTo(imageKey, userId));
        if (hasOtherUsersKey) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인이 업로드한 이미지만 정리할 수 있습니다.");
        }
    }

    private String truncateByCodePoint(String value, int maxLength) {
        if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        int cutOffset = value.offsetByCodePoints(0, maxLength - 1);
        return value.substring(0, cutOffset) + TRUNCATION_SUFFIX;
    }
}
