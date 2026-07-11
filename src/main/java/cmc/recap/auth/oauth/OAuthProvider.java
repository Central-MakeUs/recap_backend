package cmc.recap.auth.oauth;

public interface OAuthProvider {

    OAuthUserInfo verify(String providerToken);

    String providerName();
}
