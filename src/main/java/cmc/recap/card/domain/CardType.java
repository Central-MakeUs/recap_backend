package cmc.recap.card.domain;

import lombok.Getter;

@Getter
public enum CardType {

    JOB("채용/취업"),
    SHOPPING("쇼핑/상품"),
    PLACE("장소/맛집"),
    SCHEDULE("일정/예약"),
    KNOWLEDGE("정보/지식"),
    CONTENT("책/콘텐츠"),
    BENEFIT("혜택/이벤트"),
    RECORD("기록/캡처"),
    ETC("기타");

    private final String displayName;

    CardType(String displayName) {
        this.displayName = displayName;
    }
}
