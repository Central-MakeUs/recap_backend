package cmc.recap.report.service;

import cmc.recap.report.repository.ReportRepository;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReportExpirationScheduler {

    private static final Period RETENTION_PERIOD = Period.ofYears(1);

    private final ReportRepository reportRepository;

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void expireOldReports() {
        Instant cutoff = Instant.now().atZone(ZoneOffset.UTC).minus(RETENTION_PERIOD).toInstant();
        reportRepository.deleteByCreatedAtBefore(cutoff);
    }
}
