package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAdminResponse {

    private UUID          paymentId;
    private UUID          orderId;
    private UUID          userId;
    private String        userFullName;
    private String        userEmail;
    private BigDecimal    amount;
    private BigDecimal    orderTotal;
    private boolean       isPreOrder;
    private String        senderAccountName;
    private String        senderPhoneNumber;
    private String        screenshotUrl;
    private PaymentStatus paymentStatus;
    private String        adminNote;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemSummary> orderItems;
}