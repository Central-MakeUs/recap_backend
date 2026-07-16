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
import java.net.URL;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
public class CaptureService {

    private final ImagePresignedUrlProvider imagePresignedUrlProvider;
    private final InfoCardRepository infoCardRepository;
    private final S3Client s3Client;
    private final String bucketName;

    public CaptureService(
            ImagePresignedUrlProvider imagePresignedUrlProvider,
            InfoCardRepository infoCardRepository,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.imagePresignedUrlProvider = imagePresignedUrlProvider;
        this.infoCardRepository = infoCardRepository;
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
        URL originalImageUrl = imagePresignedUrlProvider.issueDownloadUrl(card.getOriginalImageKey());
        return CaptureDetailResponse.from(card, originalImageUrl.toString());
    }

    @Transactional
    public void updateFavorite(Long userId, Long captureId, boolean isFavorite) {
        InfoCard card = getOwnedCard(userId, captureId);
        card.markFavorite(isFavorite);
    }

    @Transactional
    public void delete(Long userId, Long captureId) {
        InfoCard card = getOwnedCard(userId, captureId);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(card.getOriginalImageKey())
                    .build());
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
        infoCardRepository.delete(card);
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
