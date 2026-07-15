package cmc.recap.card.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.image.ImageAnalysisProvider;
import cmc.recap.card.image.ImageAnalysisResult;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageAnalysisTaskRunnerTest {

    @Mock
    private ImageAnalysisProvider imageAnalysisProvider;
    @Mock
    private OrganizeService organizeService;

    private ImageAnalysisTaskRunner taskRunner;

    @BeforeEach
    void setUp() {
        taskRunner = new ImageAnalysisTaskRunner(imageAnalysisProvider, organizeService);
    }

    @Test
    @DisplayName("분석에 성공하면 completeImage를 호출한다")
    void 분석에_성공하면_completeImage를_호출한다() {
        ImageAnalysisResult result = new ImageAnalysisResult(
                CardType.JOB, "title", "summary", "body", "extracted");
        given(imageAnalysisProvider.analyze("captures/1/a.jpg")).willReturn(result);

        taskRunner.analyzeAndSave(1L, "captures/1/a.jpg");

        verify(organizeService).completeImage(1L, "captures/1/a.jpg", result);
        verify(organizeService, never()).failImage(any());
    }

    @Test
    @DisplayName("분석에 실패하면 failImage를 호출한다")
    void 분석에_실패하면_failImage를_호출한다() {
        given(imageAnalysisProvider.analyze("captures/1/a.jpg"))
                .willThrow(new BusinessException(ErrorCode.IMAGE_ANALYSIS_FAILED));

        taskRunner.analyzeAndSave(1L, "captures/1/a.jpg");

        verify(organizeService).failImage(1L);
        verify(organizeService, never()).completeImage(any(), any(), any());
    }

    @Test
    @DisplayName("completeImage 저장 중 예외가 나도 failImage를 호출한다")
    void completeImage_저장_중_예외가_나도_failImage를_호출한다() {
        ImageAnalysisResult result = new ImageAnalysisResult(
                CardType.JOB, "title", "summary", "body", "extracted");
        given(imageAnalysisProvider.analyze("captures/1/a.jpg")).willReturn(result);
        willThrow(new BusinessException(ErrorCode.NOT_FOUND))
                .given(organizeService).completeImage(eq(1L), eq("captures/1/a.jpg"), eq(result));

        taskRunner.analyzeAndSave(1L, "captures/1/a.jpg");

        verify(organizeService).failImage(1L);
    }
}
