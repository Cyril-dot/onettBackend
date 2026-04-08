// ─── Update Response ──────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SellerUpdateResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String location;
    private String sellerBio;
    private String storeName;
    private String storeDescription;
    private String businessAddress;
    private LocalDateTime updatedAt;
}