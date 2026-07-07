package cmc.recap.global.config;

import cmc.recap.global.exception.ErrorCodeOperationCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc은 컨텍스트에 등록된 {@link OperationCustomizer} 빈을 자동으로
 * 찾아 적용한다. {@link ErrorCodeOperationCustomizer}는 순수 POJO이므로
 * 일반 스프링 빈으로 등록하는 데 아무 제약이 없다 (ADR-0005는 JPA가
 * 생명주기를 직접 관리하는 컴포넌트에만 해당하는 원칙이며, 이 클래스는
 * 해당하지 않는다).
 */

@Configuration
@RequiredArgsConstructor
public class SwaggerConfig {

    @Value("${swagger.server.url}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RE-CAP API")
                        .description("RE-CAP 서비스의 API Docs 입니다.")
                        .version("v1.0.0"))
                .addServersItem(new Server().url(serverUrl));
    }

    @Bean
    public OperationCustomizer errorCodeOperationCustomizer() {
        return new ErrorCodeOperationCustomizer();
    }
}
