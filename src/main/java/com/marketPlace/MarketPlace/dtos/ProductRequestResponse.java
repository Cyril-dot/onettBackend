package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// For fetching a product request (admin or user view)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private BigDecimal amount;
    private Boolean paid;
    private String paystackReference;
    private ApprovalStatus approvalStatus;
    private boolean hasProduct;   // productRequest.getUserProduct() != null
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}