package cmc.recap.app;

import cmc.recap.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "App", description = "앱 버전 체크")
public interface AppApiDocs {

    @Operation(
            summary = "앱 버전 체크",
            description = "클라이언트가 실행 시점(로그인 전)에 호출해 강제 업데이트 필요 여부를 확인한다. "
                    + "platform/version이 알 수 없는 값이어도 에러를 내지 않고 forceUpdate:false로 안전하게 응답한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "버전 체크 성공"))
    ResponseEntity<ApiResponse<VersionCheckResponse>> checkVersion(
            @Parameter(description = "클라이언트 플랫폼", example = "IOS")
            String platform,
            @Parameter(description = "클라이언트 앱 버전", example = "1.0.0")
            String version);
}
