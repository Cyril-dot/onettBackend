// ─── Create Product Request ───────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    private String productDescription;

    private boolean isDiscounted;

    private StockStatus stockStatus;
    private Integer availableInDays;

    @DecimalMin(value = "0.0", inclusive = false, message = "Discount price must be greater than 0")
    private BigDecimal discountPrice;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock;

    @NotBlank(message = "Brand is required")
    private String brand;

    @NotNull(message = "Category is required")
    private UUID categoryId;  // seller picks from their existing categories

    @DecimalMin(value = "0.0", message = "Discount percentage must be 0 or greater")
    @DecimalMax(value = "100.0", message = "Discount percentage cannot exceed 100")
    private BigDecimal discountPercentage;
}