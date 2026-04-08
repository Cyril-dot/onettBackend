// ─── Login Response ───────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserLoginResponse {
    private UUID id;
    private String email;
    private Role role;
    private String accessToken;
    private String refreshToken;
}