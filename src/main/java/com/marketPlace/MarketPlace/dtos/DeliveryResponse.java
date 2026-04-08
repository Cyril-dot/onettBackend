// ─── Delivery Response ────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DeliveryResponse {
    private UUID id;
    private UUID orderId;
    private String customerName;
    private String customerEmail;
    private String deliveryAddress;
    private String recipientName;
    private String recipientPhone;
    private String deliveryNotes;
    private String deliveryStatus;
    private String trackingNumber;
    private LocalDateTime requestedAt;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime deliveredAt;
    private LocalDateTime updatedAt;
}