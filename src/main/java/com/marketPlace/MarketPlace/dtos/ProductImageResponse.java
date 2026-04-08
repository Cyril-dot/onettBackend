// ─── Product Image Response ───────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductImageResponse {
    private Long id;
    private String imageUrl;
    private String imagePublicId;
    private int displayOrder;
}