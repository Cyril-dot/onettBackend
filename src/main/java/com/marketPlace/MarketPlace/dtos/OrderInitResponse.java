package com.marketPlace.MarketPlace.dtos;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderInitResponse {
    private UUID       orderId;
    private BigDecimal total;
    private BigDecimal chargeAmount;   // Actual amount to charge (50% for pre-orders)
    private boolean    isPreOrder;
    private String     customerEmail;
    private String     customerName;
}