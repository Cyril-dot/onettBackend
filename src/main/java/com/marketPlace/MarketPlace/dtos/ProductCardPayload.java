// ─── ProductCardPayload ───────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProductCardPayload {
    private UUID conversationId;
    private UUID productId;
    private String productName;
    private String productDescription;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private boolean isDiscounted;
    private String primaryImageUrl;
    private String brand;
    private Integer stock;
    private String buyerName;
    private String buyerEmail;
    private LocalDateTime timestamp;
}