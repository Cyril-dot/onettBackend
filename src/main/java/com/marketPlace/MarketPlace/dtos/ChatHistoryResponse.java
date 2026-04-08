// ─── ChatHistoryResponse ──────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ChatHistoryResponse {
    private UUID conversationId;
    private ProductCardPayload productCard; // pinned at top of chat
    private List<MessageResponse> messages;
    private int totalMessages;
}