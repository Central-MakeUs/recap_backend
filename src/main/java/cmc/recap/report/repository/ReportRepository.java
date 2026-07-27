package cmc.recap.report.repository;

import cmc.recap.report.domain.Report;
import cmc.recap.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByUserAndCaptureId(User user, Long captureId);
}
