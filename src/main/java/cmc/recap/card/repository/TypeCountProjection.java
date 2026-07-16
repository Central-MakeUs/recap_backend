package cmc.recap.card.repository;

import cmc.recap.card.domain.CardType;
import java.time.Instant;

public interface TypeCountProjection {

    CardType getType();

    Long getCnt();

    Instant getLatest();
}
