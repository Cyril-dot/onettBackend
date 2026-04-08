package com.marketPlace.MarketPlace.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingProductsResponse {

    private List<ProductSummaryResponse> comingSoon;
    private List<ProductSummaryResponse> preOrder;
    private int totalComingSoon;
    private int totalPreOrder;
}