package com.marketPlace.MarketPlace.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemSummary {

    private UUID       productId;
    private String     productName;
    private Integer    quantity;
    private BigDecimal subTotal;
}