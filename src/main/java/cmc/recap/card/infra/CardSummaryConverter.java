package cmc.recap.card.infra;

import cmc.recap.card.domain.summary.CardSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

@Converter
@RequiredArgsConstructor
public class CardSummaryConverter implements AttributeConverter<CardSummary, String> {

    // JPA 스펙(JSR-338)이 요구하는 public 무인자 생성자를 그대로 만족시게됨.
    // 스프링/하이버네이트의 빈 컨테이너 연동에 기대지 않으므로 버전 변화에 영향받지 않게됨.
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule()); // LocalDate, LocalTime 등 직렬화 지원
    }

    @Override
    public String convertToDatabaseColumn(CardSummary summary) {
        try {
            return OBJECT_MAPPER.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카드 요약 정보 직렬화에 실패했습니다.", e);
        }
    }

    @Override
    public CardSummary convertToEntityAttribute(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, CardSummary.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카드 요약 정보 역직렬화에 실패했습니다.", e);
        }
    }
}
