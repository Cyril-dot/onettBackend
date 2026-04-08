package com.marketPlace.MarketPlace.dtos;
 
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerifyResponse {
    private String        reference;
    private UUID          orderId;
    private String        paymentStatus;
    private BigDecimal    amount;
    private LocalDateTime paidAt;
    private boolean       success;
}