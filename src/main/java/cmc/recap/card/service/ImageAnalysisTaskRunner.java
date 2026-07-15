package cmc.recap.card.service;

import cmc.recap.card.image.ImageAnalysisProvider;
import cmc.recap.card.image.ImageAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 이미지 1건의 분석·저장을 비동기로 실행한다. {@link OrganizeService}와
 * 별도 빈이어야 {@code @Async}/{@code @Transactional} 프록시가 정상
 * 동작한다(self-invocation 회피). {@link OrganizeService}가 이 빈을
 * 생성자로 주입받아 순환 참조가 되므로, {@code organizeService}는
 * {@code @Lazy}로 지연 프록시를 주입받아 순환을 끊는다.
 */
@Slf4j
@Component
public class ImageAnalysisTaskRunner {

    private final ImageAnalysisProvider imageAnalysisProvider;
    private final OrganizeService organizeService;

    public ImageAnalysisTaskRunner(
            ImageAnalysisProvider imageAnalysisProvider, @Lazy OrganizeService organizeService) {
        this.imageAnalysisProvider = imageAnalysisProvider;
        this.organizeService = organizeService;
    }

    @Async
    public void analyzeAndSave(Long batchId, String imageKey) {
        try {
            ImageAnalysisResult result = imageAnalysisProvider.analyze(imageKey);
            organizeService.completeImage(batchId, imageKey, result);
        } catch (Exception e) {
            log.error("이미지 분석/저장 실패: batchId={}, imageKey={}", batchId, imageKey, e);
            organizeService.failImage(batchId);
        }
    }
}
