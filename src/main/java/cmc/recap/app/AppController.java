package cmc.recap.app;

import cmc.recap.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
public class AppController implements AppApiDocs {

    private final AppVersionService appVersionService;

    @GetMapping("/version-check")
    @Override
    public ResponseEntity<ApiResponse<VersionCheckResponse>> checkVersion(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String version) {
        VersionCheckResponse response = appVersionService.checkVersion(platform, version);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
