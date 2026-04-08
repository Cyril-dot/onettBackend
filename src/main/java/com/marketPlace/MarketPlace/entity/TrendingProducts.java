package com.marketPlace.MarketPlace.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trending_products")
public class TrendingProducts {
    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.AUTO)
    private java.util.UUID id;

    @jakarta.persistence.OneToOne
    @jakarta.persistence.JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Integer trendingScore;

    private String keyword; // trend keyword

    private String source;

    @Builder.Default
    private LocalDateTime fetchedAt = LocalDateTime.now();
    private LocalDateTime expiresAt;

}
