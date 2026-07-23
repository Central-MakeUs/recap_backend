package cmc.recap.card.repository;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.domain.OrganizeBatch;
import cmc.recap.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
        select c from InfoCard c
        where c.user = :user
          and (lower(c.title) like lower(concat('%', :q, '%'))
            or lower(c.summary) like lower(concat('%', :q, '%'))
            or lower(c.body) like lower(concat('%', :q, '%'))
            or lower(c.extractedText) like lower(concat('%', :q, '%')))
          and (:favoriteOnly = false or c.favorite = true)
          and (:filterType is null or c.type = :filterType)
        order by
          case
            when lower(c.title) like lower(concat('%', :q, '%')) then 1
            when lower(c.summary) like lower(concat('%', :q, '%')) then 2
            when lower(c.body) like lower(concat('%', :q, '%')) then 3
            else 4
          end,
          c.createdAt desc
        """)
    Page<InfoCard> search(@Param("user") User user, @Param("q") String q,
                           @Param("favoriteOnly") boolean favoriteOnly,
                           @Param("filterType") CardType filterType,
                           Pageable pageable);
}
