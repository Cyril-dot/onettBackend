package com.marketPlace.MarketPlace.dtos;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitResponse {

    private UUID       orderId;
    private String     paymentStatus;
    private BigDecimal amount;
    private boolean    isPreOrder;
    private BigDecimal depositAmount;    // only set for pre-orders
    private BigDecimal remainingAmount;  // only set for pre-orders
    private String     screenshotUrl;
    private String     message;
}