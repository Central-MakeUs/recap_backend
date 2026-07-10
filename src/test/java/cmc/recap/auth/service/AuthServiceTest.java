package cmc.recap.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cmc.recap.auth.domain.RefreshToken;
import cmc.recap.auth.dto.request.LogoutRequest;
import cmc.recap.auth.dto.request.OAuthLoginRequest;
import cmc.recap.auth.dto.request.TokenRefreshRequest;
import cmc.recap.auth.dto.response.TokenResponse;
import cmc.recap.auth.oauth.OAuthProvider;
import cmc.recap.auth.oauth.OAuthProviderType;
import cmc.recap.auth.oauth.OAuthUserInfo;
import cmc.recap.auth.repository.RefreshTokenRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.global.jwt.JwtProvider;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private OAuthProvider kakaoProvider;
    @Mock
    private JwtProvider jwtProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, List.of(kakaoProvider), jwtProvider);
    }

    @Test
    @DisplayName("처음 보는 deviceId와 oauthId면 신규 유저를 만들고 연결한다")
    void 처음_보는_deviceId와_oauthId면_신규_유저를_만들고_연결한다() {
        OAuthLoginRequest request = new OAuthLoginRequest("device-1", "provider-token", Platform.IOS);
        given(kakaoProvider.providerName()).willReturn(OAuthProviderType.KAKAO.getCode());
        given(kakaoProvider.verify("provider-token")).willReturn(new OAuthUserInfo("oauth-1"));
        given(userRepository.findByOauthProviderAndOauthId(OAuthProviderType.KAKAO.getCode(), "oauth-1")).willReturn(Optional.empty());
        given(userRepository.findByDeviceId("device-1")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtProvider.issueAccessToken(any())).willReturn("access-token");
        given(jwtProvider.issueRefreshToken(any())).willReturn("refresh-token");
        given(jwtProvider.getExpiration("access-token")).willReturn(Instant.now().plusSeconds(1800));
        given(jwtProvider.getExpiration("refresh-token")).willReturn(Instant.now().plusSeconds(1209600));

        TokenResponse response = authService.login(OAuthProviderType.KAKAO.getCode(), request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("이미 해당 oauthId로 연결된 유저가 있으면 그 유저로 로그인한다")
    void 이미_해당_oauthId로_연결된_유저가_있으면_그_유저로_로그인한다() {
        User existingUser = User.createByDevice("device-1", Platform.IOS);
        existingUser.linkOauth(OAuthProviderType.KAKAO.getCode(), "oauth-1");
        OAuthLoginRequest request = new OAuthLoginRequest("device-2", "provider-token", Platform.ANDROID);
        given(kakaoProvider.providerName()).willReturn(OAuthProviderType.KAKAO.getCode());
        given(kakaoProvider.verify("provider-token")).willReturn(new OAuthUserInfo("oauth-1"));
        given(userRepository.findByOauthProviderAndOauthId(OAuthProviderType.KAKAO.getCode(), "oauth-1")).willReturn(Optional.of(existingUser));
        given(jwtProvider.issueAccessToken(any())).willReturn("access-token");
        given(jwtProvider.issueRefreshToken(any())).willReturn("refresh-token");
        given(jwtProvider.getExpiration(any())).willReturn(Instant.now().plusSeconds(1800));

        authService.login(OAuthProviderType.KAKAO.getCode(), request);

        verify(userRepository, never()).findByDeviceId(any());
    }

    @Test
    @DisplayName("기존 익명 유저의 deviceId로 로그인하면 그 유저에 OAuth를 병합한다")
    void 기존_익명_유저의_deviceId로_로그인하면_그_유저에_OAuth를_병합한다() {
        User anonymousUser = User.createByDevice("device-1", Platform.IOS);
        OAuthLoginRequest request = new OAuthLoginRequest("device-1", "provider-token", Platform.IOS);
        given(kakaoProvider.providerName()).willReturn(OAuthProviderType.KAKAO.getCode());
        given(kakaoProvider.verify("provider-token")).willReturn(new OAuthUserInfo("oauth-1"));
        given(userRepository.findByOauthProviderAndOauthId(OAuthProviderType.KAKAO.getCode(), "oauth-1")).willReturn(Optional.empty());
        given(userRepository.findByDeviceId("device-1")).willReturn(Optional.of(anonymousUser));
        given(jwtProvider.issueAccessToken(any())).willReturn("access-token");
        given(jwtProvider.issueRefreshToken(any())).willReturn("refresh-token");
        given(jwtProvider.getExpiration(any())).willReturn(Instant.now().plusSeconds(1800));

        authService.login(OAuthProviderType.KAKAO.getCode(), request);

        assertThat(anonymousUser.getOauthId()).isEqualTo("oauth-1");
        assertThat(anonymousUser.getOauthProvider()).isEqualTo(OAuthProviderType.KAKAO.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 다른 OAuth가 연결된 deviceId로 로그인하면 예외를 던진다")
    void 이미_다른_OAuth가_연결된_deviceId로_로그인하면_예외를_던진다() {
        User alreadyLinkedUser = User.createByDevice("device-1", Platform.IOS);
        alreadyLinkedUser.linkOauth(OAuthProviderType.APPLE.getCode(), "apple-oauth-1");
        OAuthLoginRequest request = new OAuthLoginRequest("device-1", "provider-token", Platform.IOS);
        given(kakaoProvider.providerName()).willReturn(OAuthProviderType.KAKAO.getCode());
        given(kakaoProvider.verify("provider-token")).willReturn(new OAuthUserInfo("oauth-1"));
        given(userRepository.findByOauthProviderAndOauthId(OAuthProviderType.KAKAO.getCode(), "oauth-1")).willReturn(Optional.empty());
        given(userRepository.findByDeviceId("device-1")).willReturn(Optional.of(alreadyLinkedUser));

        assertThatThrownBy(() -> authService.login(OAuthProviderType.KAKAO.getCode(), request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_LINKED_OAUTH);
    }

    @Test
    @DisplayName("지원하지 않는 provider로 로그인하면 예외를 던진다")
    void 지원하지_않는_provider로_로그인하면_예외를_던진다() {
        OAuthLoginRequest request = new OAuthLoginRequest("device-1", "provider-token", Platform.IOS);
        given(kakaoProvider.providerName()).willReturn(OAuthProviderType.KAKAO.getCode());

        assertThatThrownBy(() -> authService.login("google", request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("유효한 refreshToken이면 기존 토큰을 폐기하고 새 토큰을 발급한다")
    void 유효한_refreshToken이면_기존_토큰을_폐기하고_새_토큰을_발급한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        RefreshToken storedToken = RefreshToken.issue(user, anyHash(), Instant.now().plusSeconds(1000));
        TokenRefreshRequest request = new TokenRefreshRequest("raw-refresh-token");
        given(jwtProvider.getUserId("raw-refresh-token")).willReturn(1L);
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(storedToken));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(jwtProvider.issueAccessToken(any())).willReturn("new-access-token");
        given(jwtProvider.issueRefreshToken(any())).willReturn("new-refresh-token");
        given(jwtProvider.getExpiration(any())).willReturn(Instant.now().plusSeconds(1800));

        TokenResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(storedToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("만료된 refreshToken이면 예외를 던진다")
    void 만료된_refreshToken이면_예외를_던진다() {
        TokenRefreshRequest request = new TokenRefreshRequest("expired-token");
        willThrow(new ExpiredJwtException(null, null, "expired"))
                .given(jwtProvider).getUserId("expired-token");

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXPIRED_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("서명이 유효하지 않은 refreshToken이면 예외를 던진다")
    void 서명이_유효하지_않은_refreshToken이면_예외를_던진다() {
        TokenRefreshRequest request = new TokenRefreshRequest("tampered-token");
        willThrow(new JwtException("invalid signature"))
                .given(jwtProvider).getUserId("tampered-token");

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("DB에 없는 refreshToken이면 예외를 던진다")
    void DB에_없는_refreshToken이면_예외를_던진다() {
        TokenRefreshRequest request = new TokenRefreshRequest("unknown-token");
        given(jwtProvider.getUserId("unknown-token")).willReturn(1L);
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("이미 폐기된 refreshToken이면 예외를 던진다")
    void 이미_폐기된_refreshToken이면_예외를_던진다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        RefreshToken revokedToken = RefreshToken.issue(user, anyHash(), Instant.now().plusSeconds(1000));
        revokedToken.revoke();
        TokenRefreshRequest request = new TokenRefreshRequest("revoked-token");
        given(jwtProvider.getUserId("revoked-token")).willReturn(1L);
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("존재하는 refreshToken으로 로그아웃하면 토큰을 폐기한다")
    void 존재하는_refreshToken으로_로그아웃하면_토큰을_폐기한다() {
        User user = User.createByDevice("device-1", Platform.IOS);
        RefreshToken storedToken = RefreshToken.issue(user, anyHash(), Instant.now().plusSeconds(1000));
        LogoutRequest request = new LogoutRequest("raw-refresh-token");
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(storedToken));

        authService.logout(request);

        assertThat(storedToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 refreshToken으로 로그아웃해도 예외를 던지지 않는다")
    void 존재하지_않는_refreshToken으로_로그아웃해도_예외를_던지지_않는다() {
        LogoutRequest request = new LogoutRequest("unknown-token");
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        authService.logout(request);
    }

    private String anyHash() {
        return "hash-" + Math.random();
    }
}
