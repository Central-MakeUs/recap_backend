package cmc.recap.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppVersionService {

    private final String iosMinimumVersion;
    private final String iosUpdateUrl;
    private final String androidMinimumVersion;
    private final String androidUpdateUrl;

    public AppVersionService(
            @Value("${app.ios.minimum-version}") String iosMinimumVersion,
            @Value("${app.ios.update-url}") String iosUpdateUrl,
            @Value("${app.android.minimum-version}") String androidMinimumVersion,
            @Value("${app.android.update-url}") String androidUpdateUrl) {
        this.iosMinimumVersion = iosMinimumVersion;
        this.iosUpdateUrl = iosUpdateUrl;
        this.androidMinimumVersion = androidMinimumVersion;
        this.androidUpdateUrl = androidUpdateUrl;
    }

    public VersionCheckResponse checkVersion(String platform, String version) {
        if (!"IOS".equalsIgnoreCase(platform) && !"ANDROID".equalsIgnoreCase(platform)) {
            return VersionCheckResponse.of(false, null, null);
        }
        if ("IOS".equalsIgnoreCase(platform)) {
            return buildResponse(version, iosMinimumVersion, iosUpdateUrl);
        }
        return buildResponse(version, androidMinimumVersion, androidUpdateUrl);
    }

    private VersionCheckResponse buildResponse(String version, String minimumVersion, String updateUrl) {
        boolean forceUpdate = isForceUpdateNeeded(version, minimumVersion);
        return VersionCheckResponse.of(forceUpdate, minimumVersion, updateUrl);
    }

    private boolean isForceUpdateNeeded(String version, String minimumVersion) {
        try {
            return VersionComparator.isLowerThan(version, minimumVersion);
        } catch (Exception e) {
            return false;
        }
    }
}
