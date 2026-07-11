package cmc.recap.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-provider-unit-tests-32bytes!!";
    private static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofMinutes(30);
    private static final Duration REFRESH_TOKEN_EXPIRY = Duration.ofDays(14);

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);

    @Test
    @DisplayName("Access 토큰을 발급하면 그 토큰에서 userId를 다시 꺼낼 수 있다")
    void Access_토큰을_발급하면_userId를_다시_꺼낼_수_있다() {
        String token = jwtProvider.issueAccessToken(1L);

        assertThat(jwtProvider.getUserId(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Refresh 토큰을 발급하면 그 토큰에서 userId를 다시 꺼낼 수 있다")
    void Refresh_토큰을_발급하면_userId를_다시_꺼낼_수_있다() {
        String token = jwtProvider.issueRefreshToken(1L);

        assertThat(jwtProvider.getUserId(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Access 토큰을 발급하면 만료 시각이 발급 시각으로부터 accessTokenExpiry만큼 뒤다")
    void Access_토큰을_발급하면_만료_시각이_발급_시각으로부터_accessTokenExpiry만큼_뒤다() {
        Instant beforeIssue = Instant.now();
        String token = jwtProvider.issueAccessToken(1L);

        Instant expiration = jwtProvider.getExpiration(token);

        assertThat(expiration).isCloseTo(beforeIssue.plus(ACCESS_TOKEN_EXPIRY), within(Duration.ofSeconds(2)));
    }

    @Test
    @DisplayName("만료된 토큰에서 userId를 꺼내면 예외를 던진다")
    void 만료된_토큰에서_userId를_꺼내면_예외를_던진다() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(60);
        String expiredToken = Jwts.builder()
                .subject("1")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtProvider.getUserId(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰에서 userId를 꺼내면 예외를 던진다")
    void 다른_키로_서명된_토큰에서_userId를_꺼내면_예외를_던진다() {
        JwtProvider otherProvider = new JwtProvider(
                "other-secret-key-for-jwt-provider-unit-tests-32b!!", ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);
        String token = otherProvider.issueAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.getUserId(token))
                .isInstanceOf(JwtException.class);
    }
}
