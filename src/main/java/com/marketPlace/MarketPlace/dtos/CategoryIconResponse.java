// ─── Category Icon Response ───────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryIconResponse {
    private Long id;
    private String imageUrl;
    private String imagePublicId;
}