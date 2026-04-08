// ─── Create Product Response ──────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CreateProductResponse {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String brand;
    private Integer stock;
    private Boolean discounted;
    private BigDecimal discountPercentage;
    private BigDecimal discountPrice;
    private String productStatus;
    private CategoryResponse category;
    private List<ProductImageResponse> images;
    private ProductVideoResponse video;    // ← add this
    private LocalDateTime createdAt;

    private StockStatus stockStatus;
    private Integer availableInDays;
}