package cmc.recap.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.dto.response.HomeSummaryResponse;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.card.repository.TypeCountProjection;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InfoCardRepository infoCardRepository;
    @Mock
    private ImagePresignedUrlProvider imagePresignedUrlProvider;

    private HomeService homeService;

    @BeforeEach
    void setUp() throws Exception {
        homeService = new HomeService(userRepository, infoCardRepository, imagePresignedUrlProvider);
        given(userRepository.getReferenceById(1L)).willReturn(userWithId(1L));
        lenient().when(imagePresignedUrlProvider.issueDownloadUrl(anyString()))
                .thenReturn(URI.create("https://s3.example.com/a.jpg").toURL());
    }

    @Test
    @DisplayName("recentCaptures는 30일 이전 캡처를 제외한다")
    void recentCaptures는_30일_이전_캡처를_제외한다() {
        User user = userWithId(1L);
        InfoCard recent = cardWithId(1L, user, CardType.JOB, Instant.now());
        InfoCard old = cardWithId(2L, user, CardType.JOB, Instant.now().minus(31, ChronoUnit.DAYS));
        given(infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(any())).willReturn(List.of(recent, old));
        given(infoCardRepository.findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.countByTypeExcludingEtc(any(), eq(CardType.ETC))).willReturn(List.of());
        given(infoCardRepository.existsByUser(any())).willReturn(true);

        HomeSummaryResponse response = homeService.getSummary(1L);

        assertThat(response.recentCaptures()).extracting("captureId").containsExactly(1L);
    }

    @Test
    @DisplayName("recentCaptures는 30일 이내 캡처가 3개면 3개 모두 반환한다")
    void recentCaptures는_30일_이내_캡처가_3개면_3개_모두_반환한다() {
        User user = userWithId(1L);
        List<InfoCard> cards = List.of(
                cardWithId(1L, user, CardType.JOB, Instant.now()),
                cardWithId(2L, user, CardType.JOB, Instant.now()),
                cardWithId(3L, user, CardType.JOB, Instant.now()));
        given(infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(any())).willReturn(cards);
        given(infoCardRepository.findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.countByTypeExcludingEtc(any(), eq(CardType.ETC))).willReturn(List.of());
        given(infoCardRepository.existsByUser(any())).willReturn(true);

        HomeSummaryResponse response = homeService.getSummary(1L);

        assertThat(response.recentCaptures()).hasSize(3);
    }

    @Test
    @DisplayName("favorites는 리포지토리가 반환한 favoritedAt 내림차순을 그대로 유지한다")
    void favorites는_리포지토리가_반환한_favoritedAt_내림차순을_그대로_유지한다() {
        User user = userWithId(1L);
        InfoCard newerFavorite = cardWithId(1L, user, CardType.JOB, Instant.now());
        InfoCard olderFavorite = cardWithId(2L, user, CardType.JOB, Instant.now());
        given(infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(any()))
                .willReturn(List.of(newerFavorite, olderFavorite));
        given(infoCardRepository.countByTypeExcludingEtc(any(), eq(CardType.ETC))).willReturn(List.of());
        given(infoCardRepository.existsByUser(any())).willReturn(true);

        HomeSummaryResponse response = homeService.getSummary(1L);

        assertThat(response.favorites()).extracting("captureId").containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("topTypes는 리포지토리 조회 시 ETC를 제외 조건으로 전달한다")
    void topTypes는_리포지토리_조회_시_ETC를_제외_조건으로_전달한다() {
        given(infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.countByTypeExcludingEtc(any(), any())).willReturn(List.of());
        given(infoCardRepository.existsByUser(any())).willReturn(true);

        homeService.getSummary(1L);

        verify(infoCardRepository).countByTypeExcludingEtc(any(), eq(CardType.ETC));
    }

    @Test
    @DisplayName("topTypes는 동점일 때 리포지토리가 반환한 순서(최근 정리 캡처 포함 유형 우선)를 그대로 유지한다")
    void topTypes는_동점일_때_리포지토리가_반환한_순서를_그대로_유지한다() {
        User user = userWithId(1L);
        TypeCountProjection tiedButRecent = projection(CardType.SHOPPING, 2L, Instant.now());
        TypeCountProjection tiedButOlder = projection(CardType.PLACE, 2L, Instant.now().minus(1, ChronoUnit.DAYS));
        given(infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.countByTypeExcludingEtc(any(), eq(CardType.ETC)))
                .willReturn(List.of(tiedButRecent, tiedButOlder));
        given(infoCardRepository.findFirstByUserAndTypeOrderByCreatedAtDesc(any(), eq(CardType.SHOPPING)))
                .willReturn(Optional.of(cardWithId(1L, user, CardType.SHOPPING, Instant.now())));
        given(infoCardRepository.findFirstByUserAndTypeOrderByCreatedAtDesc(any(), eq(CardType.PLACE)))
                .willReturn(Optional.of(cardWithId(2L, user, CardType.PLACE, Instant.now())));
        given(infoCardRepository.existsByUser(any())).willReturn(true);

        HomeSummaryResponse response = homeService.getSummary(1L);

        assertThat(response.topTypes()).extracting("typeCode").containsExactly(CardType.SHOPPING, CardType.PLACE);
    }

    @Test
    @DisplayName("topTypes는 최대 4개까지만 반환한다")
    void topTypes는_최대_4개까지만_반환한다() {
        User user = userWithId(1L);
        List<TypeCountProjection> projections = List.of(
                projection(CardType.SHOPPING, 5L, Instant.now()),
                projection(CardType.JOB, 4L, Instant.now()),
                projection(CardType.PLACE, 3L, Instant.now()),
                projection(CardType.SCHEDULE, 2L, Instant.now()),
                projection(CardType.KNOWLEDGE, 1L, Instant.now()));
        given(infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.countByTypeExcludingEtc(any(), eq(CardType.ETC))).willReturn(projections);
        given(infoCardRepository.findFirstByUserAndTypeOrderByCreatedAtDesc(any(), any()))
                .willReturn(Optional.of(cardWithId(1L, user, CardType.SHOPPING, Instant.now())));
        given(infoCardRepository.existsByUser(any())).willReturn(true);

        HomeSummaryResponse response = homeService.getSummary(1L);

        assertThat(response.topTypes()).hasSize(4);
        verify(infoCardRepository, never())
                .findFirstByUserAndTypeOrderByCreatedAtDesc(any(), eq(CardType.KNOWLEDGE));
    }

    @Test
    @DisplayName("hasAnyCapture는 캡처가 0개면 false를 반환한다")
    void hasAnyCapture는_캡처가_0개면_false를_반환한다() {
        given(infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.countByTypeExcludingEtc(any(), eq(CardType.ETC))).willReturn(List.of());
        given(infoCardRepository.existsByUser(any())).willReturn(false);

        HomeSummaryResponse response = homeService.getSummary(1L);

        assertThat(response.hasAnyCapture()).isFalse();
    }

    @Test
    @DisplayName("hasAnyCapture는 캡처가 1개 이상이면 true를 반환한다")
    void hasAnyCapture는_캡처가_1개_이상이면_true를_반환한다() {
        given(infoCardRepository.findTop3ByUserOrderByCreatedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.findTop3ByUserAndFavoriteTrueOrderByFavoritedAtDesc(any())).willReturn(List.of());
        given(infoCardRepository.countByTypeExcludingEtc(any(), eq(CardType.ETC))).willReturn(List.of());
        given(infoCardRepository.existsByUser(any())).willReturn(true);

        HomeSummaryResponse response = homeService.getSummary(1L);

        assertThat(response.hasAnyCapture()).isTrue();
    }

    private User userWithId(Long id) {
        User user = User.createByDevice("device-" + id, Platform.IOS);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InfoCard cardWithId(Long id, User user, CardType type, Instant createdAt) {
        InfoCard card = InfoCard.create(
                user, type, "title", "summary", "body", "captures/1/a.jpg", "extracted", null);
        ReflectionTestUtils.setField(card, "id", id);
        ReflectionTestUtils.setField(card, "createdAt", createdAt);
        return card;
    }

    private TypeCountProjection projection(CardType type, long cnt, Instant latest) {
        return new TypeCountProjection() {
            @Override
            public CardType getType() {
                return type;
            }

            @Override
            public Long getCnt() {
                return cnt;
            }

            @Override
            public Instant getLatest() {
                return latest;
            }
        };
    }
}
