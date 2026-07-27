package cmc.recap.card.service;

import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.repository.InfoCardRepository;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Slf4j
@Component
public class OriginalImageExpirationScheduler {

    private static final Period RETENTION_PERIOD = Period.ofMonths(1);

    private final InfoCardRepository infoCardRepository;
    private final S3Client s3Client;
    private final String bucketName;

    public OriginalImageExpirationScheduler(
            InfoCardRepository infoCardRepository,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.infoCardRepository = infoCardRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void expireOldImages() {
        Instant cutoff = Instant.now().atZone(ZoneOffset.UTC).minus(RETENTION_PERIOD).toInstant();
        infoCardRepository.findByCreatedAtBeforeAndOriginalImageKeyIsNotNull(cutoff)
                .forEach(this::expireCard);
    }

    private void expireCard(InfoCard card) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(card.getOriginalImageKey())
                    .build());
            card.expireOriginalImage();
            infoCardRepository.save(card);
        } catch (Exception e) {
            log.error("원본 이미지 만료 삭제 실패: captureId={}", card.getId(), e);
        }
    }
}
