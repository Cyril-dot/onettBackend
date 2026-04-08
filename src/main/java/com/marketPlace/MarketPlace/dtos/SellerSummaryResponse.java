// ─── Seller Summary Response (nested in product details) ─────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SellerSummaryResponse {
    private UUID id;
    private String fullName;
    private String storeName;
    private String location;
    private ProfilePicResponse profilePic;
}