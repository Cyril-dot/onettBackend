// ─── SellerStoreResponse ──────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SellerStoreResponse {
    private UUID sellerId;
    private String storeName;
    private String storeDescription;
    private String sellerName;
    private String location;
    private String profilePicUrl;
    private boolean isVerified;
    private List<CategoryResponse> categories;
    private List<ProductSummaryResponse> products;
    private int totalProducts;
}