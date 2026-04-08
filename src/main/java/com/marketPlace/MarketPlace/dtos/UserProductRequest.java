package com.marketPlace.MarketPlace.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class UserProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    private String productDescription;

    @NotBlank(message = "Brand is required")
    private String brand;
}
