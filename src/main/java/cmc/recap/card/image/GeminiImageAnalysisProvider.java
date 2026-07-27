package cmc.recap.card.image;

import cmc.recap.card.domain.CardType;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
public class GeminiImageAnalysisProvider implements ImageAnalysisProvider {

    private static final String GENERATE_CONTENT_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private static final String PROMPT = CardAnalysisPrompt.build(CardAnalysisPrompt.BODY_MAX_LENGTH);

    private static final ResponseSchema RESPONSE_SCHEMA = buildResponseSchema();

    private final RestClient restClient;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String modelName;
    private final String bucketName;

    @Autowired
    public GeminiImageAnalysisProvider(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model-name}") String modelName,
            @Value("${aws.s3.bucket-name}") String bucketName,
            S3Client s3Client,
            ObjectMapper objectMapper) {
        this(RestClient.builder(), apiKey, modelName, bucketName, s3Client, objectMapper);
    }

    GeminiImageAnalysisProvider(
            RestClient.Builder restClientBuilder,
            String apiKey,
            String modelName,
            String bucketName,
            S3Client s3Client,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.bucketName = bucketName;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImageAnalysisResult analyze(String imageKey) {
        ResponseBytes<GetObjectResponse> objectBytes = downloadImage(imageKey);
        String mimeType = objectBytes.response().contentType();
        if (mimeType == null || mimeType.isBlank()) {
            throw new BusinessException(ErrorCode.IMAGE_ANALYSIS_FAILED, "이미지 MIME 타입을 확인할 수 없습니다.");
        }
        String base64Image = Base64.getEncoder().encodeToString(objectBytes.asByteArray());

        GeminiResponse response = callGeminiWithRetry(base64Image, mimeType);
        return parseResult(response);
    }

    private ResponseBytes<GetObjectResponse> downloadImage(String imageKey) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(imageKey)
                    .build();
            return s3Client.getObjectAsBytes(request);
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_VERIFICATION_FAILED, e);
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.IMAGE_ANALYSIS_FAILED, e);
        }
    }

    private GeminiResponse callGeminiWithRetry(String base64Image, String mimeType) {
        GenerateContentRequest request = buildRequest(base64Image, mimeType);
        try {
            return callGemini(request);
        } catch (RestClientException e) {
            if (!isRetryable(e)) {
                throw new BusinessException(ErrorCode.IMAGE_ANALYSIS_FAILED, e);
            }
            sleep(RETRY_DELAY);
            try {
                return callGemini(request);
            } catch (RestClientException retryException) {
                throw new BusinessException(ErrorCode.IMAGE_ANALYSIS_FAILED, retryException);
            }
        }
    }

    private GeminiResponse callGemini(GenerateContentRequest request) {
        String url = GENERATE_CONTENT_URL_TEMPLATE.formatted(modelName);
        return restClient.post()
                .uri(url)
                .header(API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);
    }

    private boolean isRetryable(RestClientException e) {
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        if (e instanceof HttpClientErrorException httpClientErrorException) {
            return httpClientErrorException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
        }
        return e instanceof ResourceAccessException;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.IMAGE_ANALYSIS_FAILED, e);
        }
    }

    private GenerateContentRequest buildRequest(String base64Image, String mimeType) {
        Part textPart = new Part(PROMPT, null);
        Part imagePart = new Part(null, new InlineData(mimeType, base64Image));
        Content content = new Content(List.of(textPart, imagePart));
        GenerationConfig generationConfig = new GenerationConfig("application/json", RESPONSE_SCHEMA);
        return new GenerateContentRequest(List.of(content), generationConfig);
    }

    private ImageAnalysisResult parseResult(GeminiResponse response) {
        try {
            String json = response.candidates().get(0).content().parts().get(0).text();
            AnalysisJson analysisJson = objectMapper.readValue(json, AnalysisJson.class);
            CardType type = CardType.valueOf(analysisJson.type());
            return new ImageAnalysisResult(
                    type, analysisJson.title(), analysisJson.summary(),
                    analysisJson.body(), analysisJson.extractedText());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.IMAGE_ANALYSIS_FAILED, e);
        }
    }

    private static ResponseSchema buildResponseSchema() {
        List<String> cardTypeNames = Arrays.stream(CardType.values()).map(Enum::name).toList();
        Map<String, PropertySchema> properties = Map.of(
                "type", new PropertySchema("STRING", cardTypeNames),
                "title", new PropertySchema("STRING", null),
                "summary", new PropertySchema("STRING", null),
                "body", new PropertySchema("STRING", null),
                "extractedText", new PropertySchema("STRING", null));
        List<String> required = List.of("type", "title", "summary", "body", "extractedText");
        return new ResponseSchema("OBJECT", properties, required);
    }

    // ---- Gemini generateContent 요청 DTO (REST 필드명은 snake_case) ----

    private record GenerateContentRequest(
            List<Content> contents,
            @JsonProperty("generation_config") GenerationConfig generationConfig) {
    }

    private record Content(List<Part> parts) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Part(String text, @JsonProperty("inline_data") InlineData inlineData) {
    }

    private record InlineData(@JsonProperty("mime_type") String mimeType, String data) {
    }

    private record GenerationConfig(
            @JsonProperty("response_mime_type") String responseMimeType,
            @JsonProperty("response_schema") ResponseSchema responseSchema) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ResponseSchema(String type, Map<String, PropertySchema> properties, List<String> required) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PropertySchema(String type, @JsonProperty("enum") List<String> enumValues) {
    }

    // ---- Gemini generateContent 응답 DTO ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiResponse(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(ResponseContent content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseContent(List<ResponsePart> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponsePart(String text) {
    }

    // ---- Gemini가 반환한 JSON 본문(parts[0].text) 파싱용 ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AnalysisJson(String type, String title, String summary, String body, String extractedText) {
    }
}
