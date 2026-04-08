// ─── SendMessageRequest ───────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "Message content cannot be empty")
    private String content;

    private Long productImageId; // optional — attach a product image
}