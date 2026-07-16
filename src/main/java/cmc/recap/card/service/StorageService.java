package cmc.recap.card.service;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.dto.response.CaptureListResponse;
import cmc.recap.card.dto.response.CaptureSummaryResponse;
import cmc.recap.card.dto.response.StorageTypeResponse;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.card.repository.TypeCountProjection;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final UserRepository userRepository;
    private final InfoCardRepository infoCardRepository;
    private final ImagePresignedUrlProvider imagePresignedUrlProvider;

    public CaptureListResponse getFavorites(Long userId) {
        User user = userRepository.getReferenceById(userId);
        List<CaptureSummaryResponse> items = infoCardRepository
                .findByUserAndFavoriteTrueOrderByFavoritedAtDesc(user).stream()
                .map(this::toCaptureSummary)
                .toList();
        return CaptureListResponse.of(items);
    }

    public CaptureListResponse getEtc(Long userId, String sortParam) {
        return getByType(userId, CardType.ETC, sortParam);
    }

    public List<StorageTypeResponse> getTypes(Long userId) {
        User user = userRepository.getReferenceById(userId);
        return infoCardRepository.countByTypeExcludingEtc(user, CardType.ETC).stream()
                .map(projection -> toStorageType(user, projection))
                .toList();
    }

    public CaptureListResponse getTypeDetail(Long userId, CardType typeCode, String sortParam) {
        if (typeCode == CardType.ETC) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return getByType(userId, typeCode, sortParam);
    }

    private CaptureListResponse getByType(Long userId, CardType type, String sortParam) {
        User user = userRepository.getReferenceById(userId);
        Sort sort = resolveSort(sortParam);
        List<CaptureSummaryResponse> items = infoCardRepository
                .findByUserAndType(user, type, sort).stream()
                .map(this::toCaptureSummary)
                .toList();
        return CaptureListResponse.of(items);
    }

    private Sort resolveSort(String sortParam) {
        return sortParam != null && "oldest".equalsIgnoreCase(sortParam.trim())
                ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();
    }

    private StorageTypeResponse toStorageType(User user, TypeCountProjection projection) {
        List<String> titles = infoCardRepository
                .findTop2ByUserAndTypeOrderByCreatedAtDesc(user, projection.getType()).stream()
                .map(InfoCard::getTitle)
                .toList();
        return StorageTypeResponse.of(projection.getType(), projection.getCnt(), titles);
    }

    private CaptureSummaryResponse toCaptureSummary(InfoCard card) {
        return CaptureSummaryResponse.from(card, issueThumbnailUrl(card.getOriginalImageKey()));
    }

    private String issueThumbnailUrl(String objectKey) {
        return imagePresignedUrlProvider.issueDownloadUrl(objectKey).toString();
    }
}
