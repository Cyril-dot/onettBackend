// ─── Update Request ───────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SellerUpdateRequest {

    private String fullName;

    @Pattern(regexp = "^(\\+233|233|0)(2[0-9]|5[0-9])[0-9]{7}$", message = "Invalid Ghana phone number")
    private String phoneNumber;

    private String location;
    private String sellerBio;
    private String storeName;
    private String storeDescription;
    private String businessAddress;
}