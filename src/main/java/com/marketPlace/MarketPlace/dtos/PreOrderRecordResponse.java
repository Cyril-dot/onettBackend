package com.marketPlace.MarketPlace.dtos;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PreOrderRecordResponse {
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private String customerName;
    private String customerEmail;
    private UUID productId;
    private String productName;
    private BigDecimal totalAmount;
    private BigDecimal depositAmount;
    private BigDecimal remainingAmount;
    private String status;                  // PreOrderStatus name
    private LocalDateTime notifiedAt;
    private LocalDateTime deliveryRequestedAt;
    private LocalDateTime secondPaymentConfirmedAt;
    private String confirmedByAdminName;
    private String adminNote;
    private LocalDateTime createdAt;
}