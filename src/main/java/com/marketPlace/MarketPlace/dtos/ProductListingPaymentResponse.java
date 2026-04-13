package com.marketPlace.MarketPlace.dtos;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListingPaymentResponse {

    private UUID       productRequestId;
    private String     screenshotUrl;
    private BigDecimal amount;
    private String     message;
}