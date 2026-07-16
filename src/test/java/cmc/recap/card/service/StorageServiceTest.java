package cmc.recap.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.dto.response.CaptureListResponse;
import cmc.recap.card.dto.response.StorageTypeResponse;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.card.repository.TypeCountProjection;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InfoCardRepository infoCardRepository;
    @Mock
    private ImagePresignedUrlProvider imagePresignedUrlProvider;

    private StorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        storageService = new StorageService(userRepository, infoCardRepository, imagePresignedUrlProvider);
        lenient().when(userRepository.getReferenceById(1L)).thenReturn(userWithId(1L));
        lenient().when(imagePresignedUrlProvider.issueDownloadUrl(anyString()))
                .thenReturn(URI.create("https://s3.example.com/a.jpg").toURL());
    }

    @Nested
    @DisplayName("getFavorites")
    class GetFavorites {

        @Test
        @DisplayName("리포지토리가 반환한 favoritedAt 내림차순을 그대로 유지한다")
        void 리포지토리가_반환한_favoritedAt_내림차순을_그대로_유지한다() {
            User user = userWithId(1L);
            InfoCard newer = cardWithId(1L, user, CardType.JOB);
            InfoCard older = cardWithId(2L, user, CardType.JOB);
            given(infoCardRepository.findByUserAndFavoriteTrueOrderByFavoritedAtDesc(any()))
                    .willReturn(List.of(newer, older));

            CaptureListResponse response = storageService.getFavorites(1L);

            assertThat(response.items()).extracting("captureId").containsExactly(1L, 2L);
            assertThat(response.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getEtc")
    class GetEtc {

        @Test
        @DisplayName("sort=oldest면 오래된순 Sort로 조회한다")
        void sort가_oldest면_오래된순_Sort로_조회한다() {
            given(infoCardRepository.findByUserAndType(any(), eq(CardType.ETC), any())).willReturn(List.of());

            storageService.getEtc(1L, "oldest");

            verify(infoCardRepository).findByUserAndType(
                    any(), eq(CardType.ETC), eq(Sort.by("createdAt").ascending()));
        }

        @Test
        @DisplayName("sort=latest면 최신순 Sort로 조회한다")
        void sort가_latest면_최신순_Sort로_조회한다() {
            given(infoCardRepository.findByUserAndType(any(), eq(CardType.ETC), any())).willReturn(List.of());

            storageService.getEtc(1L, "latest");

            verify(infoCardRepository).findByUserAndType(
                    any(), eq(CardType.ETC), eq(Sort.by("createdAt").descending()));
        }

        @Test
        @DisplayName("sort가 없거나 알 수 없는 값이면 최신순으로 폴백한다")
        void sort가_없거나_알수없는_값이면_최신순으로_폴백한다() {
            given(infoCardRepository.findByUserAndType(any(), eq(CardType.ETC), any())).willReturn(List.of());

            storageService.getEtc(1L, "invalid");
            storageService.getEtc(1L, null);

            verify(infoCardRepository, org.mockito.Mockito.times(2)).findByUserAndType(
                    any(), eq(CardType.ETC), eq(Sort.by("createdAt").descending()));
        }
    }

    @Nested
    @DisplayName("getTypes")
    class GetTypes {

        @Test
        @DisplayName("ETC를 제외 조건으로 전달한다")
        void ETC를_제외_조건으로_전달한다() {
            given(infoCardRepository.countByTypeExcludingEtc(any(), any())).willReturn(List.of());

            storageService.getTypes(1L);

            verify(infoCardRepository).countByTypeExcludingEtc(any(), eq(CardType.ETC));
        }

        @Test
        @DisplayName("대표 제목은 최대 2개까지만 담긴다")
        void 대표_제목은_최대_2개까지만_담긴다() {
            User user = userWithId(1L);
            TypeCountProjection projection = projection(CardType.JOB, 5L);
            given(infoCardRepository.countByTypeExcludingEtc(any(), eq(CardType.ETC)))
                    .willReturn(List.of(projection));
            given(infoCardRepository.findTop2ByUserAndTypeOrderByCreatedAtDesc(any(), eq(CardType.JOB)))
                    .willReturn(List.of(
                            titledCard(user, CardType.JOB, "title1"),
                            titledCard(user, CardType.JOB, "title2")));

            List<StorageTypeResponse> response = storageService.getTypes(1L);

            assertThat(response).hasSize(1);
            assertThat(response.get(0).representativeTitles()).containsExactly("title1", "title2");
            assertThat(response.get(0).count()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("getTypeDetail")
    class GetTypeDetail {

        @Test
        @DisplayName("정상 유형이면 해당 유형 캡처 목록을 반환한다")
        void 정상_유형이면_해당_유형_캡처_목록을_반환한다() {
            User user = userWithId(1L);
            given(infoCardRepository.findByUserAndType(any(), eq(CardType.JOB), any()))
                    .willReturn(List.of(cardWithId(1L, user, CardType.JOB)));

            CaptureListResponse response = storageService.getTypeDetail(1L, CardType.JOB, "latest");

            assertThat(response.items()).extracting("captureId").containsExactly(1L);
        }

        @Test
        @DisplayName("typeCode가 ETC면 INVALID_INPUT 예외를 던진다")
        void typeCode가_ETC면_INVALID_INPUT_예외를_던진다() {
            assertThatThrownBy(() -> storageService.getTypeDetail(1L, CardType.ETC, "latest"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    private User userWithId(Long id) {
        User user = User.createByDevice("device-" + id, Platform.IOS);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InfoCard cardWithId(Long id, User user, CardType type) {
        InfoCard card = InfoCard.create(
                user, type, "title", "summary", "body", "captures/1/a.jpg", "extracted", null);
        ReflectionTestUtils.setField(card, "id", id);
        ReflectionTestUtils.setField(card, "createdAt", Instant.now());
        return card;
    }

    private InfoCard titledCard(User user, CardType type, String title) {
        InfoCard card = InfoCard.create(
                user, type, title, "summary", "body", "captures/1/a.jpg", "extracted", null);
        ReflectionTestUtils.setField(card, "id", 1L);
        ReflectionTestUtils.setField(card, "createdAt", Instant.now());
        return card;
    }

    private TypeCountProjection projection(CardType type, long cnt) {
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
                return Instant.now();
            }
        };
    }
}
