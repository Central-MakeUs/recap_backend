package cmc.recap.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;

import cmc.recap.report.repository.ReportRepository;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportExpirationSchedulerTest {

    @Mock
    private ReportRepository reportRepository;

    private ReportExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReportExpirationScheduler(reportRepository);
    }

    @Test
    @DisplayName("expireOldReports는 1년 이전을 cutoff로 전달해 벌크 삭제를 요청한다")
    void expireOldReports는_1년_이전을_cutoff로_전달해_벌크_삭제를_요청한다() {
        scheduler.expireOldReports();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(reportRepository).deleteByCreatedAtBefore(cutoffCaptor.capture());
        Instant expectedCutoff = Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(1)).toInstant();
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(5, ChronoUnit.SECONDS));
    }
}
