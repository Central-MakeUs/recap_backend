package cmc.recap.card.repository;

import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.domain.OrganizeBatch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfoCardRepository extends JpaRepository<InfoCard, Long> {

    List<InfoCard> findByBatch(OrganizeBatch batch);
}
