package cmc.recap.card.dto.response;

import cmc.recap.card.domain.CardType;
import java.util.List;

public record StorageTypeResponse(CardType typeCode, long count, List<String> representativeTitles) {
    public static StorageTypeResponse of(CardType typeCode, long count, List<String> titles) {
        return new StorageTypeResponse(typeCode, count, titles);
    }
}
