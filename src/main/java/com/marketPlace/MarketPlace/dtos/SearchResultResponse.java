// ─── SearchResultResponse ─────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SearchResultResponse {
    private String keyword;
    private String location;
    private int totalResults;
    private List<ProductSummaryResponse> products;
}