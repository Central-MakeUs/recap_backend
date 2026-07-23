package cmc.recap.card.controller;

import cmc.recap.card.domain.CardType;
import cmc.recap.card.domain.SearchScope;
import cmc.recap.card.dto.response.SearchResponse;
import cmc.recap.global.dto.ApiResponse;
import cmc.recap.global.exception.ApiErrorCodes;
import cmc.recap.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Search", description = "정보카드 검색")
public interface SearchApiDocs {

    @Operation(
            summary = "정보카드 검색",
            description = "q는 trim 후 연속 공백을 한 칸으로 정규화해 1~100자만 허용한다. "
                    + "scope는 ALL/FAVORITE/ETC/TYPE 중 하나만 허용하며, scope=TYPE일 때 "
                    + "typeCode가 없으면 INVALID_INPUT을 반환한다. 제목/요약은 첫 매칭 지점만 "
                    + "하이라이트하며, 제목/요약/본문 어디에도 매칭이 없고 추출 텍스트(OCR)에서만 "
                    + "매칭되면 발췌(ocrExcerptHighlighted)를 함께 내려준다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검색 성공"))
    @ApiErrorCodes({
            ErrorCode.INVALID_INPUT
    })
    ResponseEntity<ApiResponse<SearchResponse>> search(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "검색어 (trim+공백정규화 후 1~100자)", example = "카페") String q,
            @Parameter(description = "검색 범위", example = "ALL") SearchScope scope,
            @Parameter(description = "scope=TYPE일 때 사용할 정보카드 유형", example = "KNOWLEDGE") CardType typeCode,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") int size);
}
