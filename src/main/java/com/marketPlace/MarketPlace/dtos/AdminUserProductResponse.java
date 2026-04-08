package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AdminUserProductResponse {
    private UUID sellerId;
    private LocalDateTime approvedAt;
    private UserProductResponse productResponse;
}
