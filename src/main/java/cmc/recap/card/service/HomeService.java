package cmc.recap.card.service;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.dto.response.CapturePageResponse;
import cmc.recap.card.dto.response.CaptureSummaryResponse;
import cmc.recap.card.dto.response.HomeSummaryResponse;
import cmc.recap.card.dto.response.TopTypeResponse;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.card.repository.TypeCountProjection;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final int RECENT_DAYS = 30;
    private static final int MAX_TOP_TYPES = 4;

    private final UserRepository userRepository;
    private final InfoCardRepository infoCardRepository;
    private final ImagePresignedUrlProvider imagePresignedUrlProvider;

    public HomeSummaryResponse getSummary(Long userId) {
        User user = userRepository.getReferenceById(userId);

        List<CaptureSummaryResponse> recentCaptures = getRecentCaptures(user);
        List<CaptureSummaryResponse> favorites = infoCardRepository
                .findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(user).stream()
                .map(this::toCaptureSummary)
                .toList();
        List<TopTypeResponse> topTypes = getTopTypes(user);
        boolean hasAnyCapture = infoCardRepository.existsByUser(user);

        return HomeSummaryResponse.of(recentCaptures, favorites, topTypes, hasAnyCapture);
    }

    public CapturePageResponse getRecentCapturesPage(Long userId, int page, int size) {
        User user = userRepository.getReferenceById(userId);
        Instant since = Instant.now().minus(RECENT_DAYS, ChronoUnit.DAYS);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<InfoCard> result = infoCardRepository.findByUserAndCreatedAtAfter(user, since, pageable);
        List<CaptureSummaryResponse> items = result.getContent().stream()
                .map(this::toCaptureSummary)
                .toList();
        return CapturePageResponse.of(result.getTotalElements(), result.hasNext(), items);
    }

    private List<CaptureSummaryResponse> getRecentCaptures(User user) {
        Instant since = Instant.now().minus(RECENT_DAYS, ChronoUnit.DAYS);
        return infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(user).stream()
                .filter(card -> !card.getCreatedAt().isBefore(since))
                .map(this::toCaptureSummary)
                .toList();
    }

    private List<TopTypeResponse> getTopTypes(User user) {
        return infoCardRepository.countByTypeExcludingEtc(user, CardType.ETC).stream()
                .limit(MAX_TOP_TYPES)
                .map(projection -> toTopType(user, projection))
                .filter(Objects::nonNull)
                .toList();
    }

    private TopTypeResponse toTopType(User user, TypeCountProjection projection) {
        return infoCardRepository
                .findFirstByUserAndTypeOrderByCreatedAtDesc(user, projection.getType())
                .map(representative -> TopTypeResponse.of(
                        projection.getType(), projection.getCnt(), issueThumbnailUrl(representative.getOriginalImageKey())))
                .orElse(null);
    }

    private CaptureSummaryResponse toCaptureSummary(InfoCard card) {
        return CaptureSummaryResponse.from(card, issueThumbnailUrl(card.getOriginalImageKey()));
    }

    private String issueThumbnailUrl(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        return imagePresignedUrlProvider.issueDownloadUrl(objectKey).toString();
    }
}
