package cmc.recap.card.service;

import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.dto.response.CaptureDetailResponse;
import cmc.recap.card.dto.response.UploadUrlsResponse;
import cmc.recap.card.dto.response.UploadUrlsResponse.UploadItem;
import cmc.recap.card.image.CaptureObjectKeyGenerator;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.image.PresignedUploadInfo;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.report.domain.Report;
import cmc.recap.report.domain.ReportReason;
import cmc.recap.report.repository.ReportRepository;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@Service
public class CaptureService {

    private static final int S3_DELETE_CHUNK_SIZE = 1000;

    private final ImagePresignedUrlProvider imagePresignedUrlProvider;
    private final InfoCardRepository infoCardRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final S3Client s3Client;
    private final String bucketName;

    public CaptureService(
            ImagePresignedUrlProvider imagePresignedUrlProvider,
            InfoCardRepository infoCardRepository,
            ReportRepository reportRepository,
            UserRepository userRepository,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.imagePresignedUrlProvider = imagePresignedUrlProvider;
        this.infoCardRepository = infoCardRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public UploadUrlsResponse issueUploadUrls(Long userId, int count) {
        List<UploadItem> uploads = IntStream.range(0, count)
                .mapToObj(i -> issueUploadItem(userId))
                .toList();
        return UploadUrlsResponse.of(uploads);
    }

    public CaptureDetailResponse getDetail(Long userId, Long captureId) {
        InfoCard card = getOwnedCard(userId, captureId);
        return CaptureDetailResponse.from(card, resolveOriginalImageUrl(card));
    }

    @Transactional
    public void updateFavorite(Long userId, Long captureId, boolean isFavorite) {
        InfoCard card = getOwnedCard(userId, captureId);
        card.markFavorite(isFavorite);
    }

    @Transactional
    public void delete(Long userId, Long captureId) {
        InfoCard card = getOwnedCard(userId, captureId);
        if (card.getOriginalImageKey() != null) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(card.getOriginalImageKey())
                        .build());
            } catch (SdkException e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
            }
        }
        infoCardRepository.delete(card);
    }

    @Transactional
    public void bulkDelete(Long userId, List<Long> captureIds) {
        if (captureIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        User user = userRepository.getReferenceById(userId);
        List<InfoCard> cards = infoCardRepository.findByIdInAndUser(captureIds, user);

        List<String> imageKeys = cards.stream()
                .map(InfoCard::getOriginalImageKey)
                .filter(Objects::nonNull)
                .toList();
        deleteS3ObjectsInChunks(imageKeys);

        infoCardRepository.deleteAll(cards);
    }

    @Transactional
    public void updateBody(Long userId, Long captureId, String body) {
        InfoCard card = getOwnedCard(userId, captureId);
        card.updateBody(body);
    }

    @Transactional
    public void report(Long userId, Long captureId, ReportReason reason, String detail) {
        InfoCard card = getOwnedCard(userId, captureId);
        if (reportRepository.existsByUserAndCaptureId(card.getUser(), captureId)) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        reportRepository.save(Report.create(card.getUser(), card, reason, detail));
    }

    private void deleteS3ObjectsInChunks(List<String> imageKeys) {
        for (int i = 0; i < imageKeys.size(); i += S3_DELETE_CHUNK_SIZE) {
            List<String> chunk = imageKeys.subList(i, Math.min(i + S3_DELETE_CHUNK_SIZE, imageKeys.size()));
            List<ObjectIdentifier> objectIds = chunk.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();
            try {
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder().objects(objectIds).build())
                        .build());
            } catch (SdkException e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
            }
        }
    }

    private String resolveOriginalImageUrl(InfoCard card) {
        if (card.getOriginalImageKey() == null) {
            return null;
        }
        return imagePresignedUrlProvider.issueDownloadUrl(card.getOriginalImageKey()).toString();
    }

    private UploadItem issueUploadItem(Long userId) {
        String objectKey = CaptureObjectKeyGenerator.generate(userId);
        PresignedUploadInfo uploadInfo = imagePresignedUrlProvider.issueUploadUrl(objectKey);
        return UploadItem.of(objectKey, uploadInfo.uploadUrl());
    }

    private InfoCard getOwnedCard(Long userId, Long captureId) {
        InfoCard card = infoCardRepository.findById(captureId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateOwner(userId, card);
        return card;
    }

    private void validateOwner(Long userId, InfoCard card) {
        if (!userId.equals(card.getUser().getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
