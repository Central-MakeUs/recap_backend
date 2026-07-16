package cmc.recap.card.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.recap.card.domain.BatchStatus;
import cmc.recap.card.domain.CardType;
import cmc.recap.card.dto.response.CaptureDetailResponse;
import cmc.recap.card.dto.response.OrganizeResponse;
import cmc.recap.card.dto.response.OrganizeStatusResponse;
import cmc.recap.card.dto.response.PendingResultResponse;
import cmc.recap.card.dto.response.UploadUrlsResponse;
import cmc.recap.card.dto.response.UploadUrlsResponse.UploadItem;
import cmc.recap.card.service.CaptureService;
import cmc.recap.card.service.OrganizeService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CaptureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private CaptureService captureService;

    @MockitoBean
    private OrganizeService organizeService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtProvider.issueAccessToken(1L);
    }

    @Test
    @DisplayName("유효한 count로 요청하면 200과 presigned URL 목록을 응답한다")
    void 유효한_count로_요청하면_200과_presigned_URL_목록을_응답한다() throws Exception {
        given(captureService.issueUploadUrls(eq(1L), eq(2)))
                .willReturn(UploadUrlsResponse.of(List.of(
                        UploadItem.of("captures/1/a.jpg", "https://s3.example.com/a"),
                        UploadItem.of("captures/1/b.jpg", "https://s3.example.com/b"))));

        mockMvc.perform(post("/api/v1/captures/upload-urls")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploads.length()").value(2))
                .andExpect(jsonPath("$.data.uploads[0].imageKey").value("captures/1/a.jpg"));
    }

    @Test
    @DisplayName("count가 0이면 400과 INVALID_INPUT을 응답한다")
    void count가_0이면_400과_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/captures/upload-urls")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("count가 21이면 400과 INVALID_INPUT을 응답한다")
    void count가_21이면_400과_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/captures/upload-urls")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 응답한다")
    void 인증_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/captures/upload-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("OAUTH_VERIFICATION_FAILED"));
    }

    @Test
    @DisplayName("유효한 imageKeys로 정리를 시작하면 200과 배치 정보를 응답한다")
    void 유효한_imageKeys로_정리를_시작하면_200과_배치_정보를_응답한다() throws Exception {
        given(organizeService.organize(eq(1L), eq(List.of("captures/1/a.jpg"))))
                .willReturn(OrganizeResponse.of(123L, 1, BatchStatus.PROCESSING));

        mockMvc.perform(post("/api/v1/captures/organize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageKeys\":[\"captures/1/a.jpg\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.batchId").value(123))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("imageKeys가 비어 있으면 400과 INVALID_INPUT을 응답한다")
    void imageKeys가_비어_있으면_400과_INVALID_INPUT을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/captures/organize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageKeys\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("이미 진행 중인 배치가 있으면 409와 ORGANIZE_IN_PROGRESS를 응답한다")
    void 이미_진행_중인_배치가_있으면_409와_ORGANIZE_IN_PROGRESS를_응답한다() throws Exception {
        given(organizeService.organize(eq(1L), eq(List.of("captures/1/a.jpg"))))
                .willThrow(new BusinessException(ErrorCode.ORGANIZE_IN_PROGRESS));

        mockMvc.perform(post("/api/v1/captures/organize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageKeys\":[\"captures/1/a.jpg\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ORGANIZE_IN_PROGRESS"));
    }

    @Test
    @DisplayName("정리 상태를 조회하면 200과 상태 정보를 응답한다")
    void 정리_상태를_조회하면_200과_상태_정보를_응답한다() throws Exception {
        given(organizeService.getStatus(1L, 123L))
                .willReturn(OrganizeStatusResponse.of(123L, BatchStatus.PROCESSING, 5, 2, 0));

        mockMvc.perform(get("/api/v1/captures/organize/123/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value(123))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.successCount").value(2));
    }

    @Test
    @DisplayName("다른 유저의 배치를 조회하면 404와 NOT_FOUND를 응답한다")
    void 다른_유저의_배치를_조회하면_404와_NOT_FOUND를_응답한다() throws Exception {
        given(organizeService.getStatus(1L, 123L))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/captures/organize/123/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("정리를 취소하면 204를 응답한다")
    void 정리를_취소하면_204를_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/captures/organize/123/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(organizeService).cancel(1L, 123L);
    }

    @Test
    @DisplayName("확인하지 않은 완료 배치가 있으면 200과 배치 정보를 응답한다")
    void 확인하지_않은_완료_배치가_있으면_200과_배치_정보를_응답한다() throws Exception {
        given(organizeService.getPendingResult(1L))
                .willReturn(PendingResultResponse.of(123L, BatchStatus.PARTIAL_FAILED, 4, 1));

        mockMvc.perform(get("/api/v1/captures/organize/pending-result")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value(123))
                .andExpect(jsonPath("$.data.status").value("PARTIAL_FAILED"));
    }

    @Test
    @DisplayName("확인할 배치가 없으면 200과 null 데이터를 응답한다")
    void 확인할_배치가_없으면_200과_null_데이터를_응답한다() throws Exception {
        given(organizeService.getPendingResult(1L)).willReturn(null);

        mockMvc.perform(get("/api/v1/captures/organize/pending-result")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("정리 결과를 확인 처리하면 204를 응답한다")
    void 정리_결과를_확인_처리하면_204를_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/captures/organize/123/ack")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(organizeService).ack(1L, 123L);
    }

    @Test
    @DisplayName("정보카드 상세를 조회하면 200과 상세 정보를 응답한다")
    void 정보카드_상세를_조회하면_200과_상세_정보를_응답한다() throws Exception {
        given(captureService.getDetail(1L, 10L)).willReturn(new CaptureDetailResponse(
                10L, CardType.JOB, "title", "summary", "body",
                "https://s3.example.com/original", false, Instant.now()));

        mockMvc.perform(get("/api/v1/captures/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captureId").value(10))
                .andExpect(jsonPath("$.data.title").value("title"))
                .andExpect(jsonPath("$.data.originalImageUrl").value("https://s3.example.com/original"));
    }

    @Test
    @DisplayName("다른 유저의 정보카드를 조회하면 404와 NOT_FOUND를 응답한다")
    void 다른_유저의_정보카드를_조회하면_404와_NOT_FOUND를_응답한다() throws Exception {
        given(captureService.getDetail(1L, 10L)).willThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/captures/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("즐겨찾기를 true로 설정하면 204를 응답한다")
    void 즐겨찾기를_true로_설정하면_204를_응답한다() throws Exception {
        mockMvc.perform(patch("/api/v1/captures/10/favorite")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andExpect(status().isNoContent());

        verify(captureService).updateFavorite(1L, 10L, true);
    }

    @Test
    @DisplayName("즐겨찾기를 false로 설정하면 204를 응답한다")
    void 즐겨찾기를_false로_설정하면_204를_응답한다() throws Exception {
        mockMvc.perform(patch("/api/v1/captures/10/favorite")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":false}"))
                .andExpect(status().isNoContent());

        verify(captureService).updateFavorite(1L, 10L, false);
    }

    @Test
    @DisplayName("다른 유저의 정보카드 즐겨찾기를 변경하면 404와 NOT_FOUND를 응답한다")
    void 다른_유저의_정보카드_즐겨찾기를_변경하면_404와_NOT_FOUND를_응답한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.NOT_FOUND))
                .given(captureService).updateFavorite(1L, 10L, true);

        mockMvc.perform(patch("/api/v1/captures/10/favorite")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("정보카드를 삭제하면 204를 응답한다")
    void 정보카드를_삭제하면_204를_응답한다() throws Exception {
        mockMvc.perform(delete("/api/v1/captures/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        verify(captureService).delete(1L, 10L);
    }

    @Test
    @DisplayName("다른 유저의 정보카드를 삭제하면 404와 NOT_FOUND를 응답한다")
    void 다른_유저의_정보카드를_삭제하면_404와_NOT_FOUND를_응답한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.NOT_FOUND))
                .given(captureService).delete(1L, 10L);

        mockMvc.perform(delete("/api/v1/captures/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
