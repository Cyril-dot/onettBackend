package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

// InitiatePaymentResponse.java
@Data
@Builder
public class InitiatePaymentResponse {
    private String reference;
    private String authorizationUrl;
    private String accessCode;
    private BigDecimal amount;
    private String currency;
    private String email;
}

