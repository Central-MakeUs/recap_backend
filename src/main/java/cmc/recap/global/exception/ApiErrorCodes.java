package cmc.recap.global.exception;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @ApiDocs} 인터페이스의 메서드에 붙여, 해당 엔드포인트에서 발생 가능한
 * {@link ErrorCode}를 선언한다. {@link ErrorCodeOperationCustomizer}가 이 값을
 * 읽어 Swagger 에러 응답 문서를 자동 생성한다.
 *
 * <p>실제로 {@code GlobalExceptionHandler}를 거쳐 발생할 수 있는 코드만
 * 나열한다.</p>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {
    ErrorCode[] value();
}