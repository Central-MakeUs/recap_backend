package cmc.recap.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cmc.recap.card.dto.response.UploadUrlsResponse;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.image.PresignedUploadInfo;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaptureServiceTest {

    @Mock
    private ImagePresignedUrlProvider imagePresignedUrlProvider;

    private CaptureService captureService;

    @BeforeEach
    void setUp() {
        captureService = new CaptureService(imagePresignedUrlProvider);
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
}
