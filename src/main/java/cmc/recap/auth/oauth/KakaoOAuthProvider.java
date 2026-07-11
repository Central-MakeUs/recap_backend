package cmc.recap.auth.oauth;

import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoOAuthProvider implements OAuthProvider {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;

    public KakaoOAuthProvider() {
        this(RestClient.builder());
    }

    KakaoOAuthProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuthUserInfo verify(String providerToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(KAKAO_USER_INFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (response == null || response.id() == null) {
                throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED);
            }
            return new OAuthUserInfo(String.valueOf(response.id()));
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED, e);
        }
    }

    @Override
    public String providerName() {
        return OAuthProviderType.KAKAO.getCode();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUserResponse(Long id) {
    }
}
