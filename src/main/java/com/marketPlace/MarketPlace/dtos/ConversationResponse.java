// ─── ConversationResponse ─────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ConversationResponse {
    private UUID id;
    private UUID userId;
    private String customerName;
    private String customerEmail;
    private UUID sellerId;
    private String storeName;
    private UUID productId;
    private String productName;
    private String productImageUrl;
    private String chatType;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
    private LocalDateTime createdAt;
}