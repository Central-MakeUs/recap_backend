package cmc.recap.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.global.config.JpaAuditingConfig;
import cmc.recap.report.domain.Report;
import cmc.recap.report.domain.ReportReason;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ReportRepositoryTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("deleteByCreatedAtBefore는 cutoff보다 오래된 신고만 삭제하고 이내 신고는 남긴다")
    void deleteByCreatedAtBefore는_cutoff보다_오래된_신고만_삭제하고_이내_신고는_남긴다() {
        User user = userRepository.save(User.createByDevice("device-1", Platform.IOS));
        Instant cutoff = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Report old = reportRepository.save(reportOf(user, 1L));
        Report boundary = reportRepository.save(reportOf(user, 2L));
        Report recent = reportRepository.save(reportOf(user, 3L));
        forceCreatedAt(old, cutoff.minus(1, ChronoUnit.DAYS));
        forceCreatedAt(boundary, cutoff);
        forceCreatedAt(recent, cutoff.plus(1, ChronoUnit.DAYS));

        reportRepository.deleteByCreatedAtBefore(cutoff);
        entityManager.clear();

        assertThat(reportRepository.findById(old.getId())).isEmpty();
        assertThat(reportRepository.findById(boundary.getId())).isPresent();
        assertThat(reportRepository.findById(recent.getId())).isPresent();
    }

    private Report reportOf(User user, Long captureId) {
        InfoCard card = InfoCard.create(
                user, CardType.JOB, "title", "summary", "body", null, "extracted", null);
        ReflectionTestUtils.setField(card, "id", captureId);
        return Report.create(user, card, ReportReason.OTHER, null);
    }

    private void forceCreatedAt(Report report, Instant createdAt) {
        entityManager.createQuery("update Report r set r.createdAt = :createdAt where r.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", report.getId())
                .executeUpdate();
        entityManager.clear();
    }
}
