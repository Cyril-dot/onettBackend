package com.marketPlace.MarketPlace.dtos;
import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestPayload {

    private UUID id;
    private UUID userId;
    private String userEmail;
    private BigDecimal amount;
    private Boolean paid;
    private String paystackReference;
    private ApprovalStatus approvalStatus;
    private Boolean hasProduct;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}