package cmc.recap.card.domain;

import cmc.recap.card.domain.summary.CardSummary;
import cmc.recap.card.infra.CardSummaryConverter;
import cmc.recap.global.entity.BaseTimeEntity;
import cmc.recap.user.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "info_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InfoCard extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CardType type;

    @Convert(converter = CardSummaryConverter.class)
    @Column(name = "summary", columnDefinition = "JSON", nullable = false)
    private CardSummary summary;

    @ElementCollection
    @CollectionTable(name = "info_card_keywords", joinColumns = @JoinColumn(name = "info_card_id"))
    @Column(name = "keyword")
    private List<String> keywords = new ArrayList<>();

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    private InfoCard(User user, CardType type, CardSummary summary, List<String> keywords) {
        this.user = user;
        this.type = type;
        this.summary = summary;
        this.keywords = new ArrayList<>(keywords);
        this.favorite = false;
    }

    public static InfoCard create(User user, CardType type, CardSummary summary, List<String> keywords) {
        return new InfoCard(user, type, summary, keywords);
    }

    public List<String> getKeywords() {
        return Collections.unmodifiableList(keywords);
    }

    public void changeType(CardType newType, CardSummary newSummary) {
        this.type = newType;
        this.summary = newSummary;
    }

    public void toggleFavorite() {
        this.favorite = !this.favorite;
    }
}
