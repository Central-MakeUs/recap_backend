package cmc.recap.card.domain.summary;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "cardType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JobSummary.class, name = "JOB"),
        @JsonSubTypes.Type(value = ShoppingSummary.class, name = "SHOPPING"),
        @JsonSubTypes.Type(value = PlaceSummary.class, name = "PLACE"),
        @JsonSubTypes.Type(value = ScheduleSummary.class, name = "SCHEDULE"),
        @JsonSubTypes.Type(value = KnowledgeSummary.class, name = "KNOWLEDGE"),
        @JsonSubTypes.Type(value = ContentSummary.class, name = "CONTENT"),
        @JsonSubTypes.Type(value = BenefitSummary.class, name = "BENEFIT"),
        @JsonSubTypes.Type(value = RecordSummary.class, name = "RECORD"),
        @JsonSubTypes.Type(value = EtcSummary.class, name = "ETC")
})
public interface CardSummary {
}
