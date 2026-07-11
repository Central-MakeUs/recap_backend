package cmc.recap.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppleOAuthProviderTest {

    private static final String AUDIENCE = "com.cmc.recap";
    private static final String ISSUER = "https://appleid.apple.com";

    private RSAKey rsaKey;
    private AppleOAuthProvider appleOAuthProvider;

    @BeforeEach
    void setUp() throws JOSEException {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey.toPublicJWK()));
        appleOAuthProvider = new AppleOAuthProvider(AUDIENCE, jwkSource);
    }

    @Test
    @DisplayName("유효한 identityToken을 검증하면 sub를 oauthId로 반환한다")
    void 유효한_identityToken을_검증하면_sub를_oauthId로_반환한다() throws JOSEException {
        String token = issueToken(rsaKey, validClaimsBuilder().subject("apple-sub-1").build());

        OAuthUserInfo result = appleOAuthProvider.verify(token);

        assertThat(result.oauthId()).isEqualTo("apple-sub-1");
    }

    @Test
    @DisplayName("audience가 다르면 예외를 던진다")
    void audience가_다르면_예외를_던진다() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("apple-sub-1")
                .issuer(ISSUER)
                .audience("other.bundle.id")
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build();
        String token = issueToken(rsaKey, claims);

        assertThatThrownBy(() -> appleOAuthProvider.verify(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("만료된 identityToken이면 예외를 던진다")
    void 만료된_identityToken이면_예외를_던진다() throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("apple-sub-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(Date.from(Instant.now().minusSeconds(60)))
                .build();
        String token = issueToken(rsaKey, claims);

        assertThatThrownBy(() -> appleOAuthProvider.verify(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("다른 키로 서명된 identityToken이면 예외를 던진다")
    void 다른_키로_서명된_identityToken이면_예외를_던진다() throws JOSEException {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("other-key").generate();
        String token = issueToken(otherKey, validClaimsBuilder().subject("apple-sub-1").build());

        assertThatThrownBy(() -> appleOAuthProvider.verify(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("providerName은 apple을 반환한다")
    void providerName은_apple을_반환한다() {
        assertThat(appleOAuthProvider.providerName()).isEqualTo(OAuthProviderType.APPLE.getCode());
    }

    private JWTClaimsSet.Builder validClaimsBuilder() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(Date.from(Instant.now().plusSeconds(60)));
    }

    private String issueToken(RSAKey signingKey, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
