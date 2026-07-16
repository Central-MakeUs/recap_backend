package cmc.recap.card.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.dto.response.CaptureSummaryResponse;
import cmc.recap.card.dto.response.HomeSummaryResponse;
import cmc.recap.card.dto.response.TopTypeResponse;
import cmc.recap.card.service.HomeService;
import cmc.recap.global.jwt.JwtProvider;
import java.time.Instant;
import java.util.List;
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
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private HomeService homeService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtProvider.issueAccessToken(1L);
    }

    @Test
    @DisplayName("홈 요약을 조회하면 200과 요약 정보를 응답한다")
    void 홈_요약을_조회하면_200과_요약_정보를_응답한다() throws Exception {
        CaptureSummaryResponse capture = new CaptureSummaryResponse(
                1L, "title", "summary", CardType.JOB, "https://s3.example.com/a.jpg", false, Instant.now());
        TopTypeResponse topType = TopTypeResponse.of(CardType.JOB, 3L, "https://s3.example.com/b.jpg");
        given(homeService.getSummary(1L))
                .willReturn(HomeSummaryResponse.of(List.of(capture), List.of(capture), List.of(topType), true));

        mockMvc.perform(get("/api/v1/home/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recentCaptures[0].captureId").value(1))
                .andExpect(jsonPath("$.data.topTypes[0].typeCode").value("JOB"))
                .andExpect(jsonPath("$.data.hasAnyCapture").value(true));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 응답한다")
    void 인증_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/home/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }
}
