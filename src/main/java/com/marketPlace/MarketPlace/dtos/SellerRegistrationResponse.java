// ─── Registration Response ────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SellerRegistrationResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String storeName;
    private String businessAddress;
    private String role;
    private LocalDateTime createdAt;
}