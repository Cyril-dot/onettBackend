package com.marketPlace.MarketPlace.entity;

import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
// ProductRequest - payment first, no product yet
@Entity
@Table(name = "product_request")
public class ProductRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal amount = BigDecimal.valueOf(100.00);

    @Builder.Default
    private Boolean paid = false;

    private String paystackReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    // Nullable — product is uploaded AFTER payment
    @OneToOne(mappedBy = "productRequest", fetch = FetchType.LAZY)
    private UserProduct userProduct;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}