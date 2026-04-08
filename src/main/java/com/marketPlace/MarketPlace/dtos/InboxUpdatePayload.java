// ─── InboxUpdatePayload ───────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class InboxUpdatePayload {
    private UUID conversationId;
    private String productName;
    private String productImageUrl;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private String senderType;
}