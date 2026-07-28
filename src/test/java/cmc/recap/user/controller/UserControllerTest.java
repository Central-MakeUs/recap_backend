package cmc.recap.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.recap.global.jwt.JwtProvider;
import cmc.recap.user.dto.response.AccountInfoResponse;
import cmc.recap.user.dto.response.ConsentStatusResponse;
import cmc.recap.user.dto.response.DataSummaryResponse;
import cmc.recap.user.service.ConsentService;
import cmc.recap.user.service.UserService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ConsentService consentService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtProvider.issueAccessToken(1L);
    }

    @Test
    @DisplayName("회원탈퇴를 요청하면 204를 응답한다")
    void 회원탈퇴를_요청하면_204를_응답한다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(userService).withdraw(1L);
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 응답한다")
    void 인증_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("계정 정보를 조회하면 플랫폼과 가입일을 응답한다")
    void 계정_정보를_조회하면_플랫폼과_가입일을_응답한다() throws Exception {
        Instant createdAt = Instant.parse("2026-07-01T00:00:00Z");
        given(userService.getAccountInfo(1L)).willReturn(new AccountInfoResponse("kakao", createdAt));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform").value("kakao"));
    }

    @Test
    @DisplayName("인증 없이 계정 정보를 조회하면 401을 응답한다")
    void 인증_없이_계정_정보를_조회하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("데이터 요약을 조회하면 정리된 캡처 개수를 응답한다")
    void 데이터_요약을_조회하면_정리된_캡처_개수를_응답한다() throws Exception {
        given(userService.getDataSummary(1L)).willReturn(new DataSummaryResponse(3L));

        mockMvc.perform(get("/api/v1/users/me/data-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capturedCount").value(3));
    }

    @Test
    @DisplayName("인증 없이 데이터 요약을 조회하면 401을 응답한다")
    void 인증_없이_데이터_요약을_조회하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/data-summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("계정 데이터 삭제를 요청하면 204를 응답한다")
    void 계정_데이터_삭제를_요청하면_204를_응답한다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/data")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccountData(1L);
    }

    @Test
    @DisplayName("인증 없이 계정 데이터 삭제를 요청하면 401을 응답한다")
    void 인증_없이_계정_데이터_삭제를_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/data"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("동의 상태를 조회하면 동의 여부와 동의 시각을 응답한다")
    void 동의_상태를_조회하면_동의_여부와_동의_시각을_응답한다() throws Exception {
        Instant consentedAt = Instant.parse("2026-07-29T00:00:00Z");
        given(consentService.getStatus(1L)).willReturn(new ConsentStatusResponse(true, consentedAt));

        mockMvc.perform(get("/api/v1/users/me/consent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consented").value(true));
    }

    @Test
    @DisplayName("인증 없이 동의 상태를 조회하면 401을 응답한다")
    void 인증_없이_동의_상태를_조회하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/consent"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("동의를 요청하면 204를 응답한다")
    void 동의를_요청하면_204를_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/consent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(consentService).give(1L);
    }

    @Test
    @DisplayName("인증 없이 동의를 요청하면 401을 응답한다")
    void 인증_없이_동의를_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/consent"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("동의 철회를 요청하면 204를 응답한다")
    void 동의_철회를_요청하면_204를_응답한다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/consent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(consentService).withdraw(1L);
    }

    @Test
    @DisplayName("인증 없이 동의 철회를 요청하면 401을 응답한다")
    void 인증_없이_동의_철회를_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/consent"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }
}
