package cmc.recap.card.repository;

import cmc.recap.card.domain.BatchStatus;
import cmc.recap.card.domain.OrganizeBatch;
import cmc.recap.user.domain.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizeBatchRepository extends JpaRepository<OrganizeBatch, Long> {

    boolean existsByUserAndStatus(User user, BatchStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from OrganizeBatch b where b.id = :id")
    Optional<OrganizeBatch> findByIdForUpdate(@Param("id") Long id);

    Optional<OrganizeBatch> findFirstByUserAndAcknowledgedFalseAndStatusInOrderByCreatedAtDesc(
            User user, List<BatchStatus> statuses);
}
