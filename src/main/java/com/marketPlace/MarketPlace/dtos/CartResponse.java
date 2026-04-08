// ─── Cart Response ────────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CartResponse {
    private List<CartItemResponse> items;
    private BigDecimal cartTotal;
    private BigDecimal discountedTotal;
    private Integer totalItems;
}