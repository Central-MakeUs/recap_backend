package cmc.recap.card.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.dto.response.CaptureListResponse;
import cmc.recap.card.dto.response.CaptureSummaryResponse;
import cmc.recap.card.dto.response.StorageTypeResponse;
import cmc.recap.card.service.StorageService;
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
class StorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private StorageService storageService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtProvider.issueAccessToken(1L);
    }

    @Test
    @DisplayName("즐겨찾기 목록을 조회하면 200과 목록을 응답한다")
    void 즐겨찾기_목록을_조회하면_200과_목록을_응답한다() throws Exception {
        given(storageService.getFavorites(1L)).willReturn(CaptureListResponse.of(List.of(summary(1L))));

        mockMvc.perform(get("/api/v1/storage/favorites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.items[0].captureId").value(1));
    }

    @Test
    @DisplayName("기타 유형 목록을 조회하면 200과 목록을 응답한다")
    void 기타_유형_목록을_조회하면_200과_목록을_응답한다() throws Exception {
        given(storageService.getEtc(eq(1L), eq("oldest")))
                .willReturn(CaptureListResponse.of(List.of(summary(2L))));

        mockMvc.perform(get("/api/v1/storage/etc").param("sort", "oldest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].captureId").value(2));
    }

    @Test
    @DisplayName("유형별 보기 목록을 조회하면 200과 목록을 응답한다")
    void 유형별_보기_목록을_조회하면_200과_목록을_응답한다() throws Exception {
        given(storageService.getTypes(1L))
                .willReturn(List.of(StorageTypeResponse.of(CardType.JOB, 3L, List.of("title1", "title2"))));

        mockMvc.perform(get("/api/v1/storage/types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].typeCode").value("JOB"))
                .andExpect(jsonPath("$.data[0].count").value(3))
                .andExpect(jsonPath("$.data[0].representativeTitles.length()").value(2));
    }

    @Test
    @DisplayName("유효한 유형의 캡처 목록을 조회하면 200과 목록을 응답한다")
    void 유효한_유형의_캡처_목록을_조회하면_200과_목록을_응답한다() throws Exception {
        given(storageService.getTypeDetail(eq(1L), eq(CardType.JOB), any()))
                .willReturn(CaptureListResponse.of(List.of(summary(3L))));

        mockMvc.perform(get("/api/v1/storage/types/JOB/captures")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].captureId").value(3));
    }

    @Test
    @DisplayName("유효하지 않은 typeCode 경로 변수면 400과 INVALID_INPUT을 응답한다")
    void 유효하지_않은_typeCode_경로_변수면_400과_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/storage/types/INVALID_TYPE/captures")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private CaptureSummaryResponse summary(Long id) {
        return new CaptureSummaryResponse(
                id, "title", "summary", CardType.JOB, "https://s3.example.com/a.jpg", false, Instant.now());
    }
}
