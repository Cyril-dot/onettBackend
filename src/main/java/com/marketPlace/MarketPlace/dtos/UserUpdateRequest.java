// ─── Update Request ───────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    private String fullName;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number")
    private String phoneNumber;

    private String location;

    private String bio;
}