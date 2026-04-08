// ─── CategoryProductsResponse ─────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CategoryProductsResponse {
    private CategoryResponse category;
    private List<ProductSummaryResponse> products;
    private int totalProducts;
}