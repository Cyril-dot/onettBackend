package com.marketPlace.MarketPlace.dtos;
 
import jakarta.validation.constraints.NotBlank;
import lombok.*;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
 
    @NotBlank(message = "Delivery address is required")
    private String deliveryAddress;
 
    private String notes;
}