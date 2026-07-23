package cmc.recap.card.service;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.domain.SearchScope;
import cmc.recap.card.dto.response.SearchResponse;
import cmc.recap.card.dto.response.SearchResultResponse;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.card.util.SearchHighlighter;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int Q_MIN_LENGTH = 1;
    private static final int Q_MAX_LENGTH = 100;

    private final UserRepository userRepository;
    private final InfoCardRepository infoCardRepository;
    private final ImagePresignedUrlProvider imagePresignedUrlProvider;

    public SearchResponse search(Long userId, String rawQ, SearchScope scope, CardType typeCode,
            int page, int size) {
        String q = normalizeAndValidate(rawQ);
        ScopeParams scopeParams = resolveScope(scope, typeCode);

        User user = userRepository.getReferenceById(userId);
        Page<InfoCard> cards = infoCardRepository.search(user, q, scopeParams.favoriteOnly(),
                scopeParams.filterType(), PageRequest.of(page, size));

        Page<SearchResultResponse> results = cards.map(card -> toSearchResult(card, q));
        return SearchResponse.of(results);
    }

    private String normalizeAndValidate(String rawQ) {
        String q = rawQ == null ? "" : rawQ.trim().replaceAll("\\s+", " ");
        if (q.length() < Q_MIN_LENGTH || q.length() > Q_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return q;
    }

    private ScopeParams resolveScope(SearchScope scope, CardType typeCode) {
        return switch (scope) {
            case ALL -> new ScopeParams(false, null);
            case FAVORITE -> new ScopeParams(true, null);
            case ETC -> new ScopeParams(false, CardType.ETC);
            case TYPE -> {
                if (typeCode == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
                yield new ScopeParams(false, typeCode);
            }
        };
    }

    private SearchResultResponse toSearchResult(InfoCard card, String q) {
        String titleHighlighted = SearchHighlighter.highlight(card.getTitle(), q);
        String summaryHighlighted = SearchHighlighter.highlight(card.getSummary(), q);
        boolean titleMatched = !Objects.equals(titleHighlighted, card.getTitle());
        boolean summaryMatched = !Objects.equals(summaryHighlighted, card.getSummary());
        boolean bodyMatched = !Objects.equals(
                SearchHighlighter.highlight(card.getBody(), q), card.getBody());

        String ocrExcerptHighlighted = (!titleMatched && !summaryMatched && !bodyMatched)
                ? SearchHighlighter.excerptOcrMatch(card.getExtractedText(), q)
                : null;

        String thumbnailUrl = imagePresignedUrlProvider.issueDownloadUrl(card.getOriginalImageKey())
                .toString();

        return SearchResultResponse.of(card, thumbnailUrl, titleHighlighted, summaryHighlighted,
                ocrExcerptHighlighted);
    }

    private record ScopeParams(boolean favoriteOnly, CardType filterType) {
    }
}
