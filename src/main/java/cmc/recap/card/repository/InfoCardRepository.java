package cmc.recap.card.repository;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.domain.OrganizeBatch;
import cmc.recap.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InfoCardRepository extends JpaRepository<InfoCard, Long> {

    List<InfoCard> findByBatch(OrganizeBatch batch);

    List<InfoCard> findTop3ByUserOrderByCreatedAtDesc(User user);

    List<InfoCard> findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(User user);

    boolean existsByUser(User user);

    List<InfoCard> findByUserAndFavoriteTrueOrderByFavoritedAtDesc(User user);

    List<InfoCard> findByUserAndType(User user, CardType type, Sort sort);

    List<InfoCard> findTop2ByUserAndTypeOrderByCreatedAtDesc(User user, CardType type);

    @Query("""
        select c.type as type, count(c) as cnt, max(c.createdAt) as latest
        from InfoCard c
        where c.user = :user and c.type <> :excludedType
        group by c.type
        order by count(c) desc, max(c.createdAt) desc
        """)
    List<TypeCountProjection> countByTypeExcludingEtc(
            @Param("user") User user, @Param("excludedType") CardType excludedType);

    Optional<InfoCard> findFirstByUserAndTypeOrderByCreatedAtDesc(User user, CardType type);
}
