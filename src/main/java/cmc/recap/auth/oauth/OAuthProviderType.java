package cmc.recap.auth.oauth;

public enum OAuthProviderType {

    KAKAO("kakao"),
    APPLE("apple");

    private final String code;

    OAuthProviderType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
