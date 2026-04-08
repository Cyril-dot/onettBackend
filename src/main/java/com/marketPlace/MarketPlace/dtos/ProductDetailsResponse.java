package com.marketPlace.MarketPlace.dtos;// ─── Updated ProductDetailsResponse ──────────────────────────────────────────

import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ProductDetailsResponse {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String productDescription;
    private boolean isDiscounted;
    private BigDecimal discountPercentage;
    private BigDecimal discountPrice;
    private Integer stock;
    private String brand;
    private String productStatus;
    private Integer viewsCount;
    private List<ProductImageResponse> images;
    private ProductVideoResponse video;
    private CategoryResponse category;
    private SellerSummaryResponse seller;
    private StockStatus stockStatus;
    private Integer availableInDays;
    private List<ProductSummaryResponse> relatedProducts; // ← was missing
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}