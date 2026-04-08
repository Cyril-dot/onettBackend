// ─── Update Response ──────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserUpdateResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String location;
    private String bio;
    private LocalDateTime updatedAt;
}