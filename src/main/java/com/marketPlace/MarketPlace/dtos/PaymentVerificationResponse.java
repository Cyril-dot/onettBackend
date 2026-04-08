package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

// PaymentVerificationResponse.java
@Data
@Builder
public class PaymentVerificationResponse {
    private String reference;
    private String status;      // "success", "failed", "pending"
    private String message;
    private Boolean paid;
    private UUID productRequestId; // frontend uses this to upload product after payment
}