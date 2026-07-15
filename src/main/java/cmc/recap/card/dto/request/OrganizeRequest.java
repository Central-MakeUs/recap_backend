package cmc.recap.card.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrganizeRequest(
        @NotEmpty List<String> imageKeys
) {
}
