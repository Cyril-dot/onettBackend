package com.marketPlace.MarketPlace.dtos;

import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequestAdminResponse {

    private UUID           id;
    private UUID           userId;
    private String         userFullName;
    private String         userEmail;
    private BigDecimal     amount;
    private Boolean        paid;
    private ApprovalStatus approvalStatus;
    private String         senderAccountName;
    private String         senderPhoneNumber;
    private String         screenshotUrl;
    private String         adminNote;
    private LocalDateTime  createdAt;
    private LocalDateTime  updatedAt;
}