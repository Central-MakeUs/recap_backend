package cmc.recap.global.exception;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;

/**
 * {@link ApiErrorCodes}가 붙은 컨트롤러(정확히는 {@code ApiDocs} 인터페이스)
 * 메서드에서 에러 코드 정보를 추출해 Swagger 문서의 에러 응답을 자동으로
 * 채워 넣는다.
 *
 * <p>RECAP은 RFC 7807 Problem Details를 쓰지 않는다. {@code GlobalExceptionHandler}가
 * 실제로 반환하는 {@code ApiResponse.failure(...)} 구조
 * ({@code success/data/error{code,message}})를 그대로 예시로 생성한다.
 * 이 클래스는 순수 POJO이며 JPA가 생명주기를 관리하는 컴포넌트가 아니므로,
 * ADR-0005(JPA 컴포넌트 DI 배제 원칙)와 무관하게 일반 스프링 빈으로 등록해도
 * 안전하다.</p>
 *
 * @see ApiErrorCodes
 * @see ErrorCode
 */
public class ErrorCodeOperationCustomizer implements OperationCustomizer {

    private static final String APPLICATION_JSON = "application/json";

    @Override
    public Operation customize(
            final Operation operation,
            final HandlerMethod handlerMethod
    ) {
        final List<ErrorCode> errorCodes = extractErrorCodesFromAnnotation(handlerMethod);
        if (!errorCodes.isEmpty()) {
            addErrorResponsesToOperation(operation, errorCodes);
        }
        return operation;
    }

    private List<ErrorCode> extractErrorCodesFromAnnotation(final HandlerMethod handlerMethod) {
        return Optional.ofNullable(handlerMethod.getMethodAnnotation(ApiErrorCodes.class))
                .map(ApiErrorCodes::value)
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }

    private void addErrorResponsesToOperation(
            final Operation operation,
            final List<ErrorCode> errorCodes
    ) {
        final Map<HttpStatus, List<ErrorCode>> errorsByStatus = groupErrorCodesByStatus(errorCodes);
        final ApiResponses responses = operation.getResponses();
        errorsByStatus.forEach((httpStatus, codes) ->
                responses.addApiResponse(String.valueOf(httpStatus.value()), createApiErrorResponse(codes))
        );
    }

    private Map<HttpStatus, List<ErrorCode>> groupErrorCodesByStatus(final List<ErrorCode> errorCodes) {
        return errorCodes.stream()
                .collect(Collectors.groupingBy(ErrorCode::getHttpStatus));
    }

    private ApiResponse createApiErrorResponse(final List<ErrorCode> errorCodes) {
        final String description = errorCodes.stream()
                .map(code -> "- **%s**: %s".formatted(code.getCode(), code.getMessage()))
                .collect(Collectors.joining("\n"));
        final MediaType mediaType = new MediaType();
        errorCodes.forEach(errorCode -> mediaType.addExamples(errorCode.getCode(), createErrorExample(errorCode)));
        final Content content = new Content().addMediaType(APPLICATION_JSON, mediaType);

        return new ApiResponse()
                .description(description)
                .content(content);
    }

    /**
     * RECAP의 실제 응답 포맷을 그대로 반영한 예시를 생성한다.
     * {"success": false, "data": null, "error": {"code": ..., "message": ...}}
     */
    private Example createErrorExample(final ErrorCode code) {
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code.getCode());
        error.put("message", code.getMessage());

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("error", error);

        return new Example()
                .summary(code.getCode())
                .value(body);
    }
}
