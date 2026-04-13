package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepo extends JpaRepository<Review, UUID> {

    // Get all reviews for a product
    List<Review> findByProductId(UUID productId);

    // Get all reviews by a user
    List<Review> findByUserId(UUID userId);

    // Get a specific user's review on a specific product
    Optional<Review> findByProductIdAndUserId(UUID productId, UUID userId);

    @Modifying
    @Query("DELETE FROM Review r WHERE r.product.id = :productId")
    void deleteByProductId(@Param("productId") UUID productId);

    // Check if user already reviewed a product (prevent duplicates)
    boolean existsByProductIdAndUserId(UUID productId, UUID userId);

    // Get reviews by rating
    List<Review> findByProductIdAndRating(UUID productId, Integer rating);

    // Get reviews for a product sorted by newest
    List<Review> findByProductIdOrderByCreatedAtDesc(UUID productId);

    // Count reviews per product
    long countByProductId(UUID productId);

    // Average rating for a product
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(UUID productId);

    // Top rated reviews for a product (rating >= threshold)
    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND r.rating >= :minRating ORDER BY r.rating DESC")
    List<Review> findTopRatedReviewsByProduct(UUID productId, Integer minRating);
}