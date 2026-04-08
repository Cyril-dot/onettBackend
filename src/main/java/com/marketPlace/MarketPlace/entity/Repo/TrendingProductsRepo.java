package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.TrendingProducts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrendingProductsRepo extends JpaRepository<TrendingProducts, UUID> {

    // Find all non-expired trending products sorted by score
    List<TrendingProducts> findByExpiresAtAfterOrderByTrendingScoreDesc(LocalDateTime now);

    // Find by keyword
    List<TrendingProducts> findByKeywordIgnoreCase(String keyword);

    // Find by source
    List<TrendingProducts> findBySource(String source);

    // Find trending entry for a specific product
    Optional<TrendingProducts> findByProductId(UUID productId);

    // Check if a product is already trending
    boolean existsByProductId(UUID productId);

    // Find all expired trending products (for cleanup jobs)
    List<TrendingProducts> findByExpiresAtBefore(LocalDateTime now);

    // Top N trending by score (non-expired)
    @Query("SELECT t FROM TrendingProducts t WHERE t.expiresAt > :now ORDER BY t.trendingScore DESC LIMIT :limit")
    List<TrendingProducts> findTopTrending(LocalDateTime now, int limit);

    // Find by keyword and not yet expired
    List<TrendingProducts> findByKeywordIgnoreCaseAndExpiresAtAfter(String keyword, LocalDateTime now);
}