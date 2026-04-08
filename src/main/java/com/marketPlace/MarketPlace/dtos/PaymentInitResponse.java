package com.marketPlace.MarketPlace.dtos;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitResponse {
    private String     authorizationUrl;
    private String     accessCode;
    private String     reference;
    private UUID       orderId;
    private boolean    isPreOrder;
    private BigDecimal depositAmount;    // null for normal orders
    private BigDecimal remainingAmount;  // null for normal orders
}