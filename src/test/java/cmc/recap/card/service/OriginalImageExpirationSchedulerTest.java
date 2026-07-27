package cmc.recap.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@ExtendWith(MockitoExtension.class)
class OriginalImageExpirationSchedulerTest {

    private static final String BUCKET_NAME = "test-bucket";

    @Mock
    private InfoCardRepository infoCardRepository;
    @Mock
    private S3Client s3Client;

    private OriginalImageExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OriginalImageExpirationScheduler(infoCardRepository, s3Client, BUCKET_NAME);
    }

    @Test
    @DisplayName("expireOldImages는 1개월 이전 & originalImageKey가 있는 카드만 조회 조건으로 전달한다")
    void expireOldImages는_1개월_이전_카드만_조회_조건으로_전달한다() {
        given(infoCardRepository.findByCreatedAtBeforeAndOriginalImageKeyIsNotNull(any()))
                .willReturn(List.of());

        scheduler.expireOldImages();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(infoCardRepository).findByCreatedAtBeforeAndOriginalImageKeyIsNotNull(cutoffCaptor.capture());
        Instant expectedCutoff = Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofMonths(1)).toInstant();
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("S3 삭제에 성공하면 originalImageKey를 null로 비우고 저장한다")
    void S3_삭제에_성공하면_originalImageKey를_null로_비우고_저장한다() {
        InfoCard card = cardWithId(1L, "captures/1/a.jpg");
        given(infoCardRepository.findByCreatedAtBeforeAndOriginalImageKeyIsNotNull(any()))
                .willReturn(List.of(card));

        scheduler.expireOldImages();

        assertThat(card.getOriginalImageKey()).isNull();
        verify(infoCardRepository).save(card);
    }

    @Test
    @DisplayName("S3 삭제에 실패하면 originalImageKey를 그대로 남기고 저장하지 않는다")
    void S3_삭제에_실패하면_originalImageKey를_그대로_남기고_저장하지_않는다() {
        InfoCard card = cardWithId(1L, "captures/1/a.jpg");
        given(infoCardRepository.findByCreatedAtBeforeAndOriginalImageKeyIsNotNull(any()))
                .willReturn(List.of(card));
        willThrow(SdkException.builder().message("s3 down").build())
                .given(s3Client).deleteObject(any(DeleteObjectRequest.class));

        scheduler.expireOldImages();

        assertThat(card.getOriginalImageKey()).isEqualTo("captures/1/a.jpg");
        verify(infoCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 카드는 정상 처리된다")
    void 한_건이_실패해도_나머지_카드는_정상_처리된다() {
        InfoCard failing = cardWithId(1L, "captures/1/a.jpg");
        InfoCard succeeding = cardWithId(2L, "captures/1/b.jpg");
        given(infoCardRepository.findByCreatedAtBeforeAndOriginalImageKeyIsNotNull(any()))
                .willReturn(List.of(failing, succeeding));
        willThrow(SdkException.builder().message("s3 down").build())
                .given(s3Client).deleteObject(eq(DeleteObjectRequest.builder()
                        .bucket(BUCKET_NAME).key("captures/1/a.jpg").build()));

        scheduler.expireOldImages();

        assertThat(failing.getOriginalImageKey()).isEqualTo("captures/1/a.jpg");
        assertThat(succeeding.getOriginalImageKey()).isNull();
        verify(infoCardRepository, never()).save(failing);
        verify(infoCardRepository).save(succeeding);
    }

    private InfoCard cardWithId(Long id, String originalImageKey) {
        User user = User.createByDevice("device-" + id, Platform.IOS);
        InfoCard card = InfoCard.create(
                user, CardType.JOB, "title", "summary", "body", originalImageKey, "extracted", null);
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }
}
