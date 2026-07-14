package cmc.recap.card.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ImagePresignedUrlProviderTest {

    private static final String BUCKET_NAME = "test-bucket";

    private S3ImagePresignedUrlProvider provider;

    @BeforeEach
    void setUp() {
        S3Presigner s3Presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();
        provider = new S3ImagePresignedUrlProvider(s3Presigner, BUCKET_NAME);
    }

    @Test
    @DisplayName("issueUploadUrl 하면 버킷명과 objectKey가 포함된 URL을 반환한다")
    void issueUploadUrl_하면_버킷명과_objectKey가_포함된_URL을_반환한다() {
        String objectKey = "captures/1/uuid.jpg";

        PresignedUploadInfo info = provider.issueUploadUrl(objectKey);

        assertThat(info.uploadUrl()).contains(BUCKET_NAME);
        assertThat(info.uploadUrl()).contains(objectKey);
    }

    @Test
    @DisplayName("issueUploadUrl 하면 expiresAt이 현재 시각 기준 약 10분 뒤로 설정된다")
    void issueUploadUrl_하면_expiresAt이_현재_시각_기준_약_10분_뒤로_설정된다() {
        Instant before = Instant.now();

        PresignedUploadInfo info = provider.issueUploadUrl("captures/1/uuid.jpg");

        Instant expected = before.plus(Duration.ofMinutes(10));
        assertThat(info.expiresAt()).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
    }

    @Test
    @DisplayName("issueDownloadUrl 하면 버킷명과 objectKey가 포함된 URL을 반환한다")
    void issueDownloadUrl_하면_버킷명과_objectKey가_포함된_URL을_반환한다() {
        String objectKey = "captures/1/uuid.jpg";

        String url = provider.issueDownloadUrl(objectKey).toString();

        assertThat(url).contains(BUCKET_NAME);
        assertThat(url).contains(objectKey);
    }
}
