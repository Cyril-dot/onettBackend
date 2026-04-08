// ─── Registration Request ─────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SellerRegistrationRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(\\+233|233|0)(2[0-9]|5[0-9])[0-9]{7}$", message = "Invalid Ghana phone number")
    private String phoneNumber;

    @NotBlank(message = "Store name is required")
    private String storeName;

    @NotBlank(message = "Business address is required")
    private String businessAddress;

    @NotBlank(message = "Tax ID is required")
    private String taxId;

    private String location;
    private String sellerBio;
    private String storeDescription;
}