// ─── Update Product Request ───────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateProductRequest {
    private String name;
    private String productDescription;
    private String brand;
    private Integer stock;
    private StockStatus stockStatus;
    private Integer availableInDays;
    private BigDecimal price;
    private Boolean isDiscounted;
    private BigDecimal discountPercentage;
    private UUID categoryId;
    private List<Long> imageIdsToDelete;
    private Boolean isDeleteVideo;         // ← add this
}