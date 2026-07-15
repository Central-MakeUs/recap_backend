package cmc.recap.card.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cmc.recap.card.domain.CardType;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@ExtendWith(MockitoExtension.class)
class GeminiImageAnalysisProviderTest {

    private static final String API_KEY = "test-api-key";
    private static final String MODEL_NAME = "gemini-2.5-flash";
    private static final String BUCKET_NAME = "test-bucket";
    private static final String IMAGE_KEY = "captures/1/uuid.jpg";
    private static final String GENERATE_CONTENT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent";

    @Mock
    private S3Client s3Client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer mockServer;
    private GeminiImageAnalysisProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new GeminiImageAnalysisProvider(
                restClientBuilder, API_KEY, MODEL_NAME, BUCKET_NAME, s3Client, objectMapper);
    }

    @Test
    @DisplayName("정상 응답이면 ImageAnalysisResult로 정확히 파싱한다")
    void 정상_응답이면_ImageAnalysisResult로_정확히_파싱한다() throws Exception {
        givenS3ObjectBytes("image/jpeg");
        String analysisJson = "{\"type\":\"JOB\",\"title\":\"백엔드 채용 공고\","
                + "\"summary\":\"신입 백엔드 개발자 채용\",\"body\":\"상세 설명\",\"extractedText\":\"원문 텍스트\"}";
        mockServer.expect(requestTo(GENERATE_CONTENT_URL))
                .andExpect(header("x-goog-api-key", API_KEY))
                .andRespond(withSuccess(geminiResponseBody(analysisJson), MediaType.APPLICATION_JSON));

        ImageAnalysisResult result = provider.analyze(IMAGE_KEY);

        assertThat(result.type()).isEqualTo(CardType.JOB);
        assertThat(result.title()).isEqualTo("백엔드 채용 공고");
        assertThat(result.summary()).isEqualTo("신입 백엔드 개발자 채용");
        assertThat(result.body()).isEqualTo("상세 설명");
        assertThat(result.extractedText()).isEqualTo("원문 텍스트");
    }

    @Test
    @DisplayName("CardType에 없는 문자열이 오면 IMAGE_ANALYSIS_FAILED를 던진다")
    void CardType에_없는_문자열이_오면_IMAGE_ANALYSIS_FAILED를_던진다() throws Exception {
        givenS3ObjectBytes("image/jpeg");
        String analysisJson = "{\"type\":\"NOT_A_TYPE\",\"title\":\"t\",\"summary\":\"s\","
                + "\"body\":\"b\",\"extractedText\":\"e\"}";
        mockServer.expect(requestTo(GENERATE_CONTENT_URL))
                .andRespond(withSuccess(geminiResponseBody(analysisJson), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("응답 본문이 깨진 JSON이면 IMAGE_ANALYSIS_FAILED를 던진다")
    void 응답_본문이_깨진_JSON이면_IMAGE_ANALYSIS_FAILED를_던진다() throws Exception {
        givenS3ObjectBytes("image/jpeg");
        mockServer.expect(requestTo(GENERATE_CONTENT_URL))
                .andRespond(withSuccess(geminiResponseBody("이건 JSON이 아님"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("5xx 응답이면 1회 재시도하고, 재시도도 실패하면 IMAGE_ANALYSIS_FAILED를 던진다")
    void 오류_5xx_응답이면_1회_재시도하고_재시도도_실패하면_IMAGE_ANALYSIS_FAILED를_던진다() {
        givenS3ObjectBytes("image/jpeg");
        mockServer.expect(requestTo(GENERATE_CONTENT_URL)).andRespond(withServerError());
        mockServer.expect(requestTo(GENERATE_CONTENT_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
        mockServer.verify();
    }

    @Test
    @DisplayName("429 응답이면 1회 재시도하고, 재시도도 실패하면 IMAGE_ANALYSIS_FAILED를 던진다")
    void 응답_429이면_1회_재시도하고_재시도도_실패하면_IMAGE_ANALYSIS_FAILED를_던진다() {
        givenS3ObjectBytes("image/jpeg");
        mockServer.expect(requestTo(GENERATE_CONTENT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        mockServer.expect(requestTo(GENERATE_CONTENT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
        mockServer.verify();
    }

    @Test
    @DisplayName("타임아웃이면 1회 재시도하고, 재시도도 실패하면 IMAGE_ANALYSIS_FAILED를 던진다")
    void 타임아웃이면_1회_재시도하고_재시도도_실패하면_IMAGE_ANALYSIS_FAILED를_던진다() {
        givenS3ObjectBytes("image/jpeg");
        mockServer.expect(requestTo(GENERATE_CONTENT_URL))
                .andRespond(request -> {
                    throw new IOException("simulated timeout");
                });
        mockServer.expect(requestTo(GENERATE_CONTENT_URL))
                .andRespond(request -> {
                    throw new IOException("simulated timeout");
                });

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
        mockServer.verify();
    }

    @Test
    @DisplayName("4xx 응답이면 재시도 없이 즉시 실패한다")
    void 응답_4xx이면_재시도_없이_즉시_실패한다() {
        givenS3ObjectBytes("image/jpeg");
        mockServer.expect(requestTo(GENERATE_CONTENT_URL)).andRespond(withBadRequest());

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
        mockServer.verify();
    }

    @Test
    @DisplayName("S3에 objectKey가 없으면 IMAGE_UPLOAD_VERIFICATION_FAILED를 던진다")
    void S3에_objectKey가_없으면_IMAGE_UPLOAD_VERIFICATION_FAILED를_던진다() {
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().message("no such key").build());

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("S3 접속 자체가 실패(네트워크 등)하면 IMAGE_ANALYSIS_FAILED를 던진다")
    void S3_접속_자체가_실패하면_IMAGE_ANALYSIS_FAILED를_던진다() {
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willThrow(SdkClientException.builder().message("connection failed").build());

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("S3 Content-Type이 null이면 IMAGE_ANALYSIS_FAILED를 던진다")
    void S3_ContentType이_null이면_IMAGE_ANALYSIS_FAILED를_던진다() {
        givenS3ObjectBytes(null);

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("S3 Content-Type이 공백이면 IMAGE_ANALYSIS_FAILED를 던진다")
    void S3_ContentType이_공백이면_IMAGE_ANALYSIS_FAILED를_던진다() {
        givenS3ObjectBytes("   ");

        assertThatThrownBy(() -> provider.analyze(IMAGE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_ANALYSIS_FAILED);
    }

    private void givenS3ObjectBytes(String contentType) {
        GetObjectResponse.Builder responseBuilder = GetObjectResponse.builder();
        if (contentType != null) {
            responseBuilder.contentType(contentType);
        }
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                responseBuilder.build(), "fake-image-bytes".getBytes(StandardCharsets.UTF_8));
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).willReturn(responseBytes);
    }

    private String geminiResponseBody(String analysisJsonText) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", analysisJsonText)))))));
    }
}
