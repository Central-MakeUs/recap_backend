package cmc.recap.app;

public record VersionCheckResponse(boolean forceUpdate, String minimumVersion, String updateUrl) {

    public static VersionCheckResponse of(boolean forceUpdate, String minimumVersion, String updateUrl) {
        return new VersionCheckResponse(forceUpdate, minimumVersion, updateUrl);
    }
}
