package cmc.recap.card.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.SearchScope;
import cmc.recap.card.dto.response.SearchResponse;
import cmc.recap.card.dto.response.SearchResultResponse;
import cmc.recap.card.service.SearchService;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
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
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private SearchService searchService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtProvider.issueAccessToken(1L);
    }

    @Test
    @DisplayName("검색어와 scope로 조회하면 200과 검색 결과를 응답한다")
    void 검색어와_scope로_조회하면_200과_검색_결과를_응답한다() throws Exception {
        given(searchService.search(eq(1L), eq("카페"), eq(SearchScope.ALL), isNull(), eq(0), eq(20)))
                .willReturn(SearchResponse.of(new org.springframework.data.domain.PageImpl<>(
                        List.of(result(1L)))));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "카페")
                        .param("scope", "ALL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].captureId").value(1));
    }

    @Test
    @DisplayName("scope 파라미터가 없으면 400과 INVALID_INPUT을 응답한다")
    void scope_파라미터가_없으면_400과_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "카페")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("scope 값이 잘못되면 400과 INVALID_INPUT을 응답한다")
    void scope_값이_잘못되면_400과_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "카페")
                        .param("scope", "INVALID_SCOPE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("scope=TYPE인데 typeCode가 없으면 400과 INVALID_INPUT을 응답한다")
    void scope가_TYPE인데_typeCode가_없으면_400과_INVALID_INPUT을_응답한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.INVALID_INPUT))
                .given(searchService).search(eq(1L), eq("카페"), eq(SearchScope.TYPE), isNull(), eq(0), eq(20));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "카페")
                        .param("scope", "TYPE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 응답한다")
    void 인증_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "카페")
                        .param("scope", "ALL"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }

    private SearchResultResponse result(Long id) {
        return new SearchResultResponse(id, CardType.JOB, "https://s3.example.com/a.jpg",
                "<mark>카페</mark>", "요약", null, false, Instant.now());
    }
}
