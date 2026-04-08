package com.marketPlace.MarketPlace.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestVerifyPayload {

    private UUID productRequestId;
    private String reference;
    private String status;
    private String message;
    private Boolean paid;
}