package cmc.recap.report.repository;

import cmc.recap.report.domain.Report;
import cmc.recap.user.domain.User;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByUserAndCaptureId(User user, Long captureId);

    @Modifying
    @Query("delete from Report r where r.user = :user")
    void deleteByUser(@Param("user") User user);

    @Modifying
    @Query("delete from Report r where r.createdAt < :cutoff")
    void deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
