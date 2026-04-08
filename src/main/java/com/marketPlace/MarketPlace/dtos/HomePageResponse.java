// ─── HomePageResponse ─────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class HomePageResponse {
    private List<ProductSummaryResponse> featuredProducts;
    private List<ProductSummaryResponse> newArrivals;
    private List<ProductSummaryResponse> trendingProducts;
    private List<ProductSummaryResponse> discountedProducts;
    private List<CategoryResponse> categories;
}