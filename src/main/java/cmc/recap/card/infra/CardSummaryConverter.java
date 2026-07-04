package cmc.recap.card.infra;

import cmc.recap.card.domain.summary.CardSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

@Converter
@RequiredArgsConstructor
public class CardSummaryConverter implements AttributeConverter<CardSummary, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(CardSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카드 요약 정보 직렬화에 실패했습니다.", e);
        }
    }

    @Override
    public CardSummary convertToEntityAttribute(String json) {
        try {
            return objectMapper.readValue(json, CardSummary.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카드 요약 정보 역직렬화에 실패했습니다.", e);
        }
    }
}
