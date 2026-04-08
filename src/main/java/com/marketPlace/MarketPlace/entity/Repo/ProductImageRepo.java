package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepo extends JpaRepository<ProductImage, Long> {

    // Get all images for a product ordered by display position
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(UUID productId);

    // Get the primary/cover image (displayOrder = 0)
    Optional<ProductImage> findByProductIdAndDisplayOrder(UUID productId, int displayOrder);

    // Lookup by Cloudinary public ID (for updates/deletions)
    Optional<ProductImage> findByImagePublicId(String imagePublicId);

    // Check duplicate Cloudinary upload
    boolean existsByImagePublicId(String imagePublicId);

    // Count images for a product (enforce upload limits)
    long countByProductId(UUID productId);

    // Delete all images for a product
    @Modifying
    @Transactional
    void deleteByProductId(UUID productId);

    // Delete a specific image by its Cloudinary public ID
    @Modifying
    @Transactional
    void deleteByImagePublicId(String imagePublicId);

    // Reorder — fetch all except primary for a product
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.displayOrder > 0 ORDER BY pi.displayOrder ASC")
    List<ProductImage> findNonPrimaryImages(UUID productId);
}