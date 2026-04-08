package com.marketPlace.MarketPlace.entity;

import com.marketPlace.MarketPlace.entity.Enums.ProductStatus;
import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(columnDefinition = "TEXT")
    private String productDescription;

    @Column(columnDefinition = "TEXT")
    private String brand;

    // --- Stock ---

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus productStatus = ProductStatus.ACTIVE;

    // --- Discount ---

    @Builder.Default
    private Boolean discounted = false;

    private BigDecimal discountPercentage;

    private BigDecimal discountPrice;

    // --- Analytics ---

    @Builder.Default
    private Integer viewsCount = 0;

    // --- Media ---

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ProductVideo productVideo;

    // --- Timestamps ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockStatus stockStatus;

    @Column(nullable = true)
    private Integer availableInDays;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}