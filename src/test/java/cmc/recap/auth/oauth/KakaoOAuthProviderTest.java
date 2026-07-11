package cmc.recap.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuthProviderTest {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer mockServer;
    private KakaoOAuthProvider kakaoOAuthProvider;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        kakaoOAuthProvider = new KakaoOAuthProvider(restClientBuilder);
    }

    @Test
    @DisplayName("카카오 사용자 정보 조회에 성공하면 id를 oauthId로 반환한다")
    void 카카오_사용자_정보_조회에_성공하면_id를_oauthId로_반환한다() {
        mockServer.expect(requestTo(KAKAO_USER_INFO_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("{\"id\": 123456789}", MediaType.APPLICATION_JSON));

        OAuthUserInfo result = kakaoOAuthProvider.verify("test-token");

        assertThat(result.oauthId()).isEqualTo("123456789");
    }

    @Test
    @DisplayName("카카오 서버가 인증 실패 응답을 반환하면 예외를 던진다")
    void 카카오_서버가_인증_실패_응답을_반환하면_예외를_던진다() {
        mockServer.expect(requestTo(KAKAO_USER_INFO_URL))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> kakaoOAuthProvider.verify("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("providerName은 kakao를 반환한다")
    void providerName은_kakao를_반환한다() {
        assertThat(kakaoOAuthProvider.providerName()).isEqualTo(OAuthProviderType.KAKAO.getCode());
    }
}
