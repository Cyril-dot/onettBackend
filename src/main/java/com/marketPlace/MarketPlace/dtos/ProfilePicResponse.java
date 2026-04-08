// ─── Profile Pic DTO ──────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfilePicResponse {
    private Long id;
    private String imageUrl;
    private String imagePublicId;
}