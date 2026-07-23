package cmc.recap.card.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.global.config.JpaAuditingConfig;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class InfoCardRepositoryTest {

    @Autowired
    private InfoCardRepository infoCardRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("search는 order by CASE가 있어도 COUNT 쿼리를 정확히 계산한다")
    void search는_order_by_CASE가_있어도_COUNT_쿼리를_정확히_계산한다() {
        User user = userRepository.save(User.createByDevice("device-1", Platform.IOS));
        infoCardRepository.save(InfoCard.create(user, CardType.ETC, "cat title", "summary", "body", "key1", null, null));
        infoCardRepository.save(InfoCard.create(user, CardType.ETC, "title", "cat summary", "body", "key2", null, null));
        infoCardRepository.save(InfoCard.create(user, CardType.ETC, "title", "summary", "cat body", "key3", null, null));
        infoCardRepository.save(InfoCard.create(user, CardType.ETC, "title", "summary", "body", "key4", "cat in ocr text", null));

        Page<InfoCard> page = infoCardRepository.search(
                user, "cat", false, null, PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).hasSize(1);
    }
}
