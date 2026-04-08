// ─── Cart Item Response ───────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CartItemResponse {
    private UUID cartItemId;
    private UUID productId;
    private String productName;
    private String brand;
    private String primaryImageUrl;
    private BigDecimal unitPrice;
    private BigDecimal originalPrice;
    private boolean isDiscounted;
    private Integer quantity;
    private BigDecimal subTotal;
    private Integer stock;
    private LocalDateTime addedAt;
}