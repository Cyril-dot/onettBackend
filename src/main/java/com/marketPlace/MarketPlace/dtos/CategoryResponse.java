// ─── Category Response ────────────────────────────────────────────────────────

package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CategoryResponse {
    private UUID id;
    private String name;
    private String slug;
    private CategoryIconResponse icon;
}