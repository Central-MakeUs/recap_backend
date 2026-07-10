package cmc.recap.auth.service;

import cmc.recap.auth.domain.RefreshToken;
import cmc.recap.auth.dto.request.LogoutRequest;
import cmc.recap.auth.dto.request.OAuthLoginRequest;
import cmc.recap.auth.dto.request.TokenRefreshRequest;
import cmc.recap.auth.dto.response.TokenResponse;
import cmc.recap.auth.oauth.OAuthProvider;
import cmc.recap.auth.oauth.OAuthUserInfo;
import cmc.recap.auth.repository.RefreshTokenRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.global.jwt.JwtProvider;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final List<OAuthProvider> oauthProviders;
    private final JwtProvider jwtProvider;

    @Transactional
    public TokenResponse login(String providerName, OAuthLoginRequest request) {
        OAuthProvider oauthProvider = resolveProvider(providerName);
        OAuthUserInfo userInfo = oauthProvider.verify(request.providerToken());
        User user = findOrCreateUser(providerName, userInfo, request);
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(TokenRefreshRequest request) {
        String rawToken = request.refreshToken();
        Long userId = parseRefreshTokenUserId(rawToken);
        RefreshToken storedToken = findUsableRefreshToken(rawToken);
        storedToken.revoke();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return issueTokens(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .ifPresent(RefreshToken::revoke);
    }

    private OAuthProvider resolveProvider(String providerName) {
        return oauthProviders.stream()
                .filter(provider -> provider.providerName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT, "지원하지 않는 provider입니다: " + providerName));
    }

    private User findOrCreateUser(String providerName, OAuthUserInfo userInfo, OAuthLoginRequest request) {
        return userRepository.findByOauthProviderAndOauthId(providerName, userInfo.oauthId())
                .orElseGet(() -> linkOauthToUser(providerName, userInfo, request));
    }

    private User linkOauthToUser(String providerName, OAuthUserInfo userInfo, OAuthLoginRequest request) {
        User user = userRepository.findByDeviceId(request.deviceId())
                .orElseGet(() -> userRepository.save(User.createByDevice(request.deviceId(), request.platform())));
        user.linkOauth(providerName, userInfo.oauthId());
        return user;
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.issueAccessToken(user.getId());
        String refreshToken = jwtProvider.issueRefreshToken(user.getId());
        Instant refreshExpiry = jwtProvider.getExpiration(refreshToken);
        refreshTokenRepository.save(RefreshToken.issue(user, hash(refreshToken), refreshExpiry));
        return TokenResponse.of(accessToken, refreshToken, jwtProvider.getExpiration(accessToken));
    }

    private Long parseRefreshTokenUserId(String rawToken) {
        try {
            return jwtProvider.getUserId(rawToken);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN, e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, e);
        }
    }

    private RefreshToken findUsableRefreshToken(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!token.isUsable()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return token;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
