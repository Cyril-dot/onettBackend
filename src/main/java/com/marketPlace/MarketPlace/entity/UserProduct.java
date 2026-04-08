package com.marketPlace.MarketPlace.entity;

import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_product")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // FIX: unique = true enforces the 1-to-1 payment → product contract at DB level
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_request_id", nullable = false, unique = true)
    private ProductRequest productRequest;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String productDescription;

    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    // FIX: mappedBy changed from "userProduct" → "product" to match the
    //      field name used in UserProductImage and the service builder
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<UserProductImage> images = new ArrayList<>();

    // FIX: removed BigDecimal import (unused) and manual timestamp setters —
    //      @CreationTimestamp and @UpdateTimestamp handle these automatically
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder.Default
    private int updateCount = 0;
}