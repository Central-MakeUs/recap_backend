package cmc.recap.card.util;

import java.util.Locale;

public final class SearchHighlighter {

    private static final int SEARCH_EXCERPT_PADDING = 20;
    private static final String ELLIPSIS = "…";
    private static final String MARK_OPEN = "<mark>";
    private static final String MARK_CLOSE = "</mark>";

    private SearchHighlighter() {
    }

    public static String highlight(String text, String query) {
        if (text == null) {
            return text;
        }
        int matchStart = indexOfIgnoreCase(text, query);
        if (matchStart < 0) {
            return text;
        }
        int matchEnd = matchStart + query.length();
        return text.substring(0, matchStart) + MARK_OPEN + text.substring(matchStart, matchEnd)
                + MARK_CLOSE + text.substring(matchEnd);
    }

    public static String excerptOcrMatch(String extractedText, String query) {
        if (extractedText == null) {
            return null;
        }
        int matchStart = indexOfIgnoreCase(extractedText, query);
        if (matchStart < 0) {
            return null;
        }
        int matchEnd = matchStart + query.length();

        int textLengthCp = extractedText.codePointCount(0, extractedText.length());
        int matchStartCp = extractedText.codePointCount(0, matchStart);
        int matchEndCp = extractedText.codePointCount(0, matchEnd);

        int excerptStartCp = Math.max(0, matchStartCp - SEARCH_EXCERPT_PADDING);
        int excerptEndCp = Math.min(textLengthCp, matchEndCp + SEARCH_EXCERPT_PADDING);

        int excerptStart = extractedText.offsetByCodePoints(0, excerptStartCp);
        int excerptEnd = extractedText.offsetByCodePoints(0, excerptEndCp);

        boolean truncatedFront = excerptStartCp > 0;
        boolean truncatedBack = excerptEndCp < textLengthCp;

        String before = extractedText.substring(excerptStart, matchStart);
        String matched = extractedText.substring(matchStart, matchEnd);
        String after = extractedText.substring(matchEnd, excerptEnd);

        StringBuilder excerpt = new StringBuilder();
        if (truncatedFront) {
            excerpt.append(ELLIPSIS);
        }
        excerpt.append(before).append(MARK_OPEN).append(matched).append(MARK_CLOSE).append(after);
        if (truncatedBack) {
            excerpt.append(ELLIPSIS);
        }
        return excerpt.toString();
    }

    private static int indexOfIgnoreCase(String text, String query) {
        if (query == null || query.isEmpty()) {
            return -1;
        }
        return text.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
    }
}
