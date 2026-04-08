package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserProductResponse {
    private UUID id;
    private String name;
    private String brand;
    private String productDescription; // renamed from description to match entity field
    private ApprovalStatus approvalStatus;
    private int updateCount;           // tracks how many edits the user has made (max 3)
    private List<ProductImageResponse> images;
    private LocalDateTime createdAt;
}