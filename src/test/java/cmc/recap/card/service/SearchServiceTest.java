package cmc.recap.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.domain.SearchScope;
import cmc.recap.card.dto.response.SearchResponse;
import cmc.recap.card.dto.response.SearchResultResponse;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.Platform;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InfoCardRepository infoCardRepository;
    @Mock
    private ImagePresignedUrlProvider imagePresignedUrlProvider;

    private SearchService searchService;

    @BeforeEach
    void setUp() throws Exception {
        searchService = new SearchService(userRepository, infoCardRepository, imagePresignedUrlProvider);
        lenient().when(userRepository.getReferenceById(1L)).thenReturn(userWithId(1L));
        lenient().when(imagePresignedUrlProvider.issueDownloadUrl(anyString()))
                .thenReturn(URI.create("https://s3.example.com/a.jpg").toURL());
    }

    @Nested
    @DisplayName("q 검증")
    class QValidation {

        @Test
        @DisplayName("q가 공백만 있으면 INVALID_INPUT 예외를 던진다")
        void q가_공백만_있으면_INVALID_INPUT_예외를_던진다() {
            assertThatThrownBy(() -> searchService.search(1L, "   ", SearchScope.ALL, null, 0, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("q가 101자 이상이면 INVALID_INPUT 예외를 던진다")
        void q가_101자_이상이면_INVALID_INPUT_예외를_던진다() {
            String tooLong = "가".repeat(101);

            assertThatThrownBy(() -> searchService.search(1L, tooLong, SearchScope.ALL, null, 0, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("q의 앞뒤 공백과 중간 연속 공백을 정규화해 리포지토리에 전달한다")
        void q의_앞뒤_공백과_중간_연속_공백을_정규화해_리포지토리에_전달한다() {
            given(infoCardRepository.search(any(), anyString(), eq(false), isNull(), any()))
                    .willReturn(emptyPage());

            searchService.search(1L, "  카페   추천  ", SearchScope.ALL, null, 0, 20);

            ArgumentCaptor<String> qCaptor = ArgumentCaptor.forClass(String.class);
            verify(infoCardRepository).search(any(), qCaptor.capture(), eq(false), isNull(), any());
            assertThat(qCaptor.getValue()).isEqualTo("카페 추천");
        }
    }

    @Nested
    @DisplayName("scope 매핑")
    class ScopeMapping {

        @Test
        @DisplayName("scope=ALL이면 favoriteOnly=false, filterType=null로 조회한다")
        void scope가_ALL이면_favoriteOnly_false_filterType_null로_조회한다() {
            given(infoCardRepository.search(any(), anyString(), eq(false), isNull(), any()))
                    .willReturn(emptyPage());

            searchService.search(1L, "카페", SearchScope.ALL, null, 0, 20);

            verify(infoCardRepository).search(any(), anyString(), eq(false), isNull(), any());
        }

        @Test
        @DisplayName("scope=FAVORITE이면 favoriteOnly=true로 조회한다")
        void scope가_FAVORITE이면_favoriteOnly_true로_조회한다() {
            given(infoCardRepository.search(any(), anyString(), eq(true), isNull(), any()))
                    .willReturn(emptyPage());

            searchService.search(1L, "카페", SearchScope.FAVORITE, null, 0, 20);

            verify(infoCardRepository).search(any(), anyString(), eq(true), isNull(), any());
        }

        @Test
        @DisplayName("scope=ETC이면 filterType=ETC로 조회한다")
        void scope가_ETC이면_filterType_ETC로_조회한다() {
            given(infoCardRepository.search(any(), anyString(), eq(false), eq(CardType.ETC), any()))
                    .willReturn(emptyPage());

            searchService.search(1L, "카페", SearchScope.ETC, null, 0, 20);

            verify(infoCardRepository).search(any(), anyString(), eq(false), eq(CardType.ETC), any());
        }

        @Test
        @DisplayName("scope=TYPE이면 요청의 typeCode로 필터링한다")
        void scope가_TYPE이면_요청의_typeCode로_필터링한다() {
            given(infoCardRepository.search(any(), anyString(), eq(false), eq(CardType.JOB), any()))
                    .willReturn(emptyPage());

            searchService.search(1L, "카페", SearchScope.TYPE, CardType.JOB, 0, 20);

            verify(infoCardRepository).search(any(), anyString(), eq(false), eq(CardType.JOB), any());
        }

        @Test
        @DisplayName("scope=TYPE인데 typeCode가 없으면 INVALID_INPUT 예외를 던진다")
        void scope가_TYPE인데_typeCode가_없으면_INVALID_INPUT_예외를_던진다() {
            assertThatThrownBy(() -> searchService.search(1L, "카페", SearchScope.TYPE, null, 0, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("결과 조립")
    class AssembleResult {

        @Test
        @DisplayName("리포지토리가 반환한 매칭 우선순위 순서를 그대로 유지한다")
        void 리포지토리가_반환한_매칭_우선순위_순서를_그대로_유지한다() {
            User user = userWithId(1L);
            InfoCard titleMatch = cardWithId(1L, user, "카페 추천", "요약", "본문", null);
            InfoCard bodyMatch = cardWithId(2L, user, "제목", "요약", "카페 위치 안내", null);
            given(infoCardRepository.search(any(), eq("카페"), eq(false), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(titleMatch, bodyMatch)));

            SearchResponse response = searchService.search(1L, "카페", SearchScope.ALL, null, 0, 20);

            assertThat(response.items()).extracting(SearchResultResponse::captureId)
                    .containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("title/summary/body 중 하나라도 매칭되면 ocrExcerptHighlighted는 null이다")
        void title_summary_body_중_하나라도_매칭되면_ocrExcerptHighlighted는_null이다() {
            User user = userWithId(1L);
            InfoCard card = cardWithId(1L, user, "카페 추천", "요약", "본문", "카페 관련 OCR 텍스트");
            given(infoCardRepository.search(any(), eq("카페"), eq(false), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(card)));

            SearchResponse response = searchService.search(1L, "카페", SearchScope.ALL, null, 0, 20);

            assertThat(response.items().get(0).ocrExcerptHighlighted()).isNull();
        }

        @Test
        @DisplayName("title/summary/body 어디에도 매칭이 없고 extractedText만 매칭되면 발췌를 채운다")
        void title_summary_body_매칭이_없고_extractedText만_매칭되면_발췌를_채운다() {
            User user = userWithId(1L);
            InfoCard card = cardWithId(1L, user, "제목", "요약", "본문", "여기 카페 위치가 나온다");
            given(infoCardRepository.search(any(), eq("카페"), eq(false), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(card)));

            SearchResponse response = searchService.search(1L, "카페", SearchScope.ALL, null, 0, 20);

            assertThat(response.items().get(0).ocrExcerptHighlighted()).contains("<mark>카페</mark>");
        }

        @Test
        @DisplayName("Page의 count/hasNext를 응답에 그대로 반영한다")
        void Page의_count_hasNext를_응답에_그대로_반영한다() {
            User user = userWithId(1L);
            InfoCard card = cardWithId(1L, user, "카페", "요약", "본문", null);
            Page<InfoCard> page = new PageImpl<>(List.of(card), PageRequest.of(0, 1), 5);
            given(infoCardRepository.search(any(), eq("카페"), eq(false), isNull(), any()))
                    .willReturn(page);

            SearchResponse response = searchService.search(1L, "카페", SearchScope.ALL, null, 0, 1);

            assertThat(response.count()).isEqualTo(5);
            assertThat(response.hasNext()).isTrue();
        }
    }

    private Page<InfoCard> emptyPage() {
        return new PageImpl<>(List.of());
    }

    private User userWithId(Long id) {
        User user = User.createByDevice("device-" + id, Platform.IOS);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InfoCard cardWithId(Long id, User user, String title, String summary, String body,
            String extractedText) {
        InfoCard card = InfoCard.create(
                user, CardType.JOB, title, summary, body, "captures/1/a.jpg", extractedText, null);
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }
}
