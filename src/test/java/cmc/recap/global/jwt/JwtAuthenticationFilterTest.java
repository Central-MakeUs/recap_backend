package cmc.recap.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이 있으면 SecurityContext에 인증 정보를 설정한다")
    void 유효한_Bearer_토큰이_있으면_SecurityContext에_인증_정보를_설정한다() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Bearer valid-token");
        given(jwtProvider.getUserId("valid-token")).willReturn(1L);

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증을 설정하지 않는다")
    void Authorization_헤더가_없으면_인증을_설정하지_않는다() throws Exception {
        given(request.getHeader("Authorization")).willReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 접두사가 없으면 인증을 설정하지 않는다")
    void Bearer_접두사가_없으면_인증을_설정하지_않는다() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("만료된 토큰이면 인증을 설정하지 않고 체인을 계속 진행한다")
    void 만료된_토큰이면_인증을_설정하지_않고_체인을_계속_진행한다() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Bearer expired-token");
        willThrow(new ExpiredJwtException(null, null, "expired"))
                .given(jwtProvider).getUserId("expired-token");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효한 토큰으로 필터를 통과하면 filterChain 호출 시점에 MDC에 userId가 설정된다")
    void 유효한_토큰으로_필터를_통과하면_filterChain_호출_시점에_MDC에_userId가_설정된다() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Bearer valid-token");
        given(jwtProvider.getUserId("valid-token")).willReturn(1L);
        AtomicReference<String> capturedUserId = new AtomicReference<>();
        willAnswer(invocation -> {
            capturedUserId.set(MDC.get("userId"));
            return null;
        }).given(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(capturedUserId.get()).isEqualTo("1");
    }

    @Test
    @DisplayName("필터 처리가 끝나면 MDC의 userId가 정리된다")
    void 필터_처리가_끝나면_MDC의_userId가_정리된다() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Bearer valid-token");
        given(jwtProvider.getUserId("valid-token")).willReturn(1L);

        filter.doFilter(request, response, filterChain);

        assertThat(MDC.get("userId")).isNull();
    }
}
