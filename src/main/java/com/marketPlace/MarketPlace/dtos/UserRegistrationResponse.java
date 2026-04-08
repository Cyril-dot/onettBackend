// ─── Registration Response ────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserRegistrationResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String location;
    private Role role;
    private LocalDateTime createdAt;
}