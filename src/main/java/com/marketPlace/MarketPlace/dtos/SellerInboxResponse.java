// ─── SellerInboxResponse ──────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SellerInboxResponse {
    private UUID conversationId;
    // Product card — shown as conversation header
    private UUID productId;
    private String productName;
    private String productDescription;
    private BigDecimal productPrice;
    private String productImageUrl;
    private boolean isDiscounted;
    // Buyer info
    private String buyerName;
    private String buyerEmail;
    // Message preview
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
    private String chatType;
    private LocalDateTime createdAt;
}