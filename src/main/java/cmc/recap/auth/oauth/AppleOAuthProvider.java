package cmc.recap.auth.oauth;

import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AppleOAuthProvider implements OAuthProvider {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String VALID_APPLE_AUDIENCE = "com.cmc.recap"; // iOS Bundle ID

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public AppleOAuthProvider() {
        this(JWKSourceBuilder.create(toUrl(APPLE_JWKS_URL)).build());
    }

    AppleOAuthProvider(JWKSource<SecurityContext> jwkSource) {
        this.jwtProcessor = createJwtProcessor(jwkSource);
    }

    @Override
    public OAuthUserInfo verify(String providerToken) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(providerToken, null);
            return new OAuthUserInfo(claims.getSubject());
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED, e);
        }
    }

    @Override
    public String providerName() {
        return "apple";
    }

    private static ConfigurableJWTProcessor<SecurityContext> createJwtProcessor(JWKSource<SecurityContext> jwkSource) {
        JWSKeySelector<SecurityContext> keySelector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);

        JWTClaimsSet exactMatchClaims = new JWTClaimsSet.Builder()
                .issuer(APPLE_ISSUER)
                .audience(VALID_APPLE_AUDIENCE)
                .build();

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(exactMatchClaims, Set.of("sub", "exp")));
        return processor;
    }

    private static URL toUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Apple JWKS URL이 올바르지 않습니다.", e);
        }
    }
}
