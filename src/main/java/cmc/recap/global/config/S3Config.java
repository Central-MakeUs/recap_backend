package cmc.recap.global.config;

import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.image.S3ImagePresignedUrlProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean
    public ImagePresignedUrlProvider imagePresignedUrlProvider(S3Presigner s3Presigner) {
        return new S3ImagePresignedUrlProvider(s3Presigner, bucketName);
    }
}
