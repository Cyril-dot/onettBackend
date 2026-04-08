// ─── AiChatResponse ───────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AiChatResponse {
    private String reply;
    private String sessionType;
    private List<ProductSummaryResponse> products;
    private LocalDateTime timestamp;
}