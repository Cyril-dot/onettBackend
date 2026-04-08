package com.marketPlace.MarketPlace.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "image_search_logs")
public class ImageSearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private String imagePublicId;

    @Column(columnDefinition = "TEXT")
    private String aiDescription;

    // ✅ Fixed: ManyToMany for a list of matched products
    @ManyToMany
    @JoinTable(
            name = "image_search_log_products",
            joinColumns = @JoinColumn(name = "log_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    @Builder.Default
    private List<Product> matchedProducts = new ArrayList<>();

    @Builder.Default
    private LocalDateTime searchedAt = LocalDateTime.now();
}