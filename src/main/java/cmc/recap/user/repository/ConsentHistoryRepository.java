package cmc.recap.user.repository;

import cmc.recap.user.domain.ConsentHistory;
import cmc.recap.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, Long> {

    Optional<ConsentHistory> findFirstByUserOrderByCreatedAtDesc(User user);
}
