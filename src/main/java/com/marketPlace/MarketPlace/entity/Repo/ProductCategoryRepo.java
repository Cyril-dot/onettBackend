package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepo extends JpaRepository<ProductCategory, UUID> {

    // Lookup by slug (for URL-based filtering)
    Optional<ProductCategory> findBySlug(String slug);

    // Lookup by name (case-insensitive)
    Optional<ProductCategory> findByNameIgnoreCase(String name);

    // Check duplicates before creating
    boolean existsBySlug(String slug);
    boolean existsByNameIgnoreCase(String name);

    // Get all categories for a specific seller
    List<ProductCategory> findBySellerId(UUID sellerId);

    // Get category by product
    Optional<ProductCategory> findByProductId(UUID productId);

    // Search categories by partial name (for search/autocomplete)
    List<ProductCategory> findByNameContainingIgnoreCase(String keyword);

    // Get all categories with their seller info
    @Query("SELECT pc FROM ProductCategory pc JOIN FETCH pc.seller WHERE pc.seller.id = :sellerId")
    List<ProductCategory> findBySellersWithDetails(UUID sellerId);

    // Count categories per seller
    long countBySellerId(UUID sellerId);
}