package cmc.recap.user.dto.response;

import cmc.recap.user.domain.ConsentAction;
import cmc.recap.user.domain.ConsentHistory;
import java.time.Instant;
import java.util.Optional;

public record ConsentStatusResponse(boolean consented, Instant consentedAt) {
    public static ConsentStatusResponse from(Optional<ConsentHistory> latest) {
        if (latest.isEmpty() || latest.get().getAction() == ConsentAction.WITHDRAWN) {
            return new ConsentStatusResponse(false, null);
        }
        return new ConsentStatusResponse(true, latest.get().getCreatedAt());
    }
}
