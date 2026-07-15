package cmc.recap.card.service;

import cmc.recap.card.domain.InfoCard;
import org.springframework.stereotype.Service;

/**
 * 정리(Organize) 처리 서비스의 최소 골격. 이슈 #13 STAGE 3(정리 시작
 * 핵심 로직)에서 {@link cmc.recap.card.image.ImageAnalysisProvider} 연결과
 * {@code OrganizeBatch} 오케스트레이션이 이 클래스에 채워질 예정이다.
 * 지금은 {@link InfoCard#create}에 넘기기 전 title/summary를 도메인
 * 제약(길이)에 맞게 정규화하는 책임만 둔다(STAGE 4 범위).
 */
@Service
public class OrganizeService {

    private static final String TRUNCATION_SUFFIX = "…";

    public String normalizeTitle(String title) {
        return truncateByCodePoint(title, InfoCard.TITLE_MAX_LENGTH);
    }

    public String normalizeSummary(String summary) {
        return truncateByCodePoint(summary, InfoCard.SUMMARY_MAX_LENGTH);
    }

    private String truncateByCodePoint(String value, int maxLength) {
        if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        int cutOffset = value.offsetByCodePoints(0, maxLength - 1);
        return value.substring(0, cutOffset) + TRUNCATION_SUFFIX;
    }
}
