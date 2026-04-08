// ─── StartConversationRequest ─────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class StartConversationRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;
}