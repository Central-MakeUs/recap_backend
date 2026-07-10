package cmc.recap.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.recap.auth.dto.response.TokenResponse;
import cmc.recap.auth.service.AuthService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("유효한 로그인 요청이면 200과 토큰을 응답한다")
    void 유효한_로그인_요청이면_200과_토큰을_응답한다() throws Exception {
        given(authService.login(eq("kakao"), any()))
                .willReturn(TokenResponse.of("access", "refresh", Instant.now().plusSeconds(1800)));

        mockMvc.perform(post("/api/v1/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":"device-1","providerToken":"token","platform":"IOS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    @DisplayName("deviceId가 비어 있으면 400을 응답한다")
    void deviceId가_비어_있으면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId":"","providerToken":"token","platform":"IOS"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refreshToken이 비어 있으면 400을 응답한다")
    void refreshToken이_비어_있으면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그아웃 요청이 성공하면 200을 응답한다")
    void 로그아웃_요청이_성공하면_200을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(any());
    }

    @Test
    @DisplayName("인증 없이 보호된 엔드포인트에 접근하면 401을 응답한다")
    void 인증_없이_보호된_엔드포인트에_접근하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }
}
