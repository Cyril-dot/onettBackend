// ─── Product Summary Response (for listings/feeds) ────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ProductSummaryResponse {
    private UUID id;
    private String name;
    private BigDecimal price;
    private boolean isDiscounted;
    private BigDecimal discountPercentage;
    private BigDecimal discountPrice;
    private String brand;
    private String productStatus;
    private Integer stock;
    private Integer viewsCount;
    private String primaryImageUrl;
    private String categoryName;
    private String categorySlug;
    private boolean hasVideo;          // ← add this
    private StockStatus stockStatus;
    private Integer availableInDays;
}