package com.marketPlace.MarketPlace.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaystackInitiatePayload {

    private UUID productRequestId;
    private String reference;
    private String authorizationUrl;
    private String accessCode;
    private BigDecimal amount;
    private String currency;
    private String email;
}