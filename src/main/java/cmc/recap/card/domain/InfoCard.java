package cmc.recap.card.domain;

import cmc.recap.global.entity.BaseTimeEntity;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
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
import java.time.Instant;
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

    public static final int TITLE_MAX_LENGTH = 30;
    public static final int SUMMARY_MAX_LENGTH = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CardType type;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "summary", length = SUMMARY_MAX_LENGTH)
    private String summary;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "original_image_key", nullable = false, length = 255)
    private String originalImageKey;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText; // OCR 원문, 검색 전용, 응답 미노출

    @ElementCollection
    @CollectionTable(name = "info_card_keywords", joinColumns = @JoinColumn(name = "info_card_id"))
    @Column(name = "keyword")
    private List<String> keywords = new ArrayList<>();

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @Column(name = "favorited_at")
    private Instant favoritedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organize_batch_id")
    private OrganizeBatch batch;

    public static InfoCard create(User user, CardType type, String title,
                                   String summary, String body,
                                   String originalImageKey, String extractedText,
                                   OrganizeBatch batch) {
        validateTitle(title);
        InfoCard card = new InfoCard();
        card.user = user;
        card.type = type;
        card.title = title;
        card.summary = summary;
        card.body = body;
        card.originalImageKey = originalImageKey;
        card.extractedText = extractedText;
        card.batch = batch;
        card.favorite = false;
        return card;
    }

    /** 명시적 값으로 즐겨찾기 상태를 설정한다. 멱등적이라 재시도에 안전하다. */
    public void markFavorite(boolean isFavorite) {
        this.favorite = isFavorite;
        this.favoritedAt = isFavorite ? Instant.now() : null;
    }

    public List<String> getKeywords() {
        return Collections.unmodifiableList(keywords);
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제목은 필수입니다.");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제목은 " + TITLE_MAX_LENGTH + "자를 초과할 수 없습니다.");
        }
    }
}
