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
public class ProductRequestInitiateResponse {

    // The ProductRequest ID created in DB — frontend stores this
    // and sends it back when uploading the product after payment
    private UUID productRequestId;

    // Paystack reference — used to verify payment
    private String reference;

    // Paystack payment URL — redirect user here to pay
    private String authorizationUrl;

    // Paystack access code — used if paying via Paystack popup/inline
    private String accessCode;

    // Always 100.00 GHS in your case
    private BigDecimal amount;

    // Always "GHS" in your case
    private String currency;

    // User's email — Paystack needs this
    private String email;
}