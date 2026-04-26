package com.marketPlace.MarketPlace.dtos;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID                    orderId;

    // ── Customer Info ──────────────────────────────────────────
    private String                  customerName;
    private String                  customerEmail;
    private String                  customerPhone;          // NEW — User.phone

    // ── Order Info ─────────────────────────────────────────────
    private String                  orderStatus;
    private BigDecimal              total;
    private String                  deliveryAddress;
    private String                  notes;
    private boolean                 canCancel;              // based on 2-hr window

    // ── Payment Info ───────────────────────────────────────────
    private String                  paymentStatus;
    private String                  paymentReference;       // screenshot URL

    // ── Sender / Account Details (from Payment entity) ─────────
    private String                  accountName;            // NEW — Payment.senderAccountName
    private String                  accountNumber;          // NEW — Payment.senderPhoneNumber (MoMo number)
    private String                  bank;                   // NEW — "Mobile Money" or provider name

    // ── Items ──────────────────────────────────────────────────
    private List<OrderItemResponse> orderItems;

    // ── Timestamps ─────────────────────────────────────────────
    private LocalDateTime           createdAt;
    private LocalDateTime           updatedAt;
}