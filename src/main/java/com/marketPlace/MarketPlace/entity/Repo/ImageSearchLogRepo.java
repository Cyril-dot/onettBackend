package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.ImageSearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ImageSearchLogRepo extends JpaRepository<ImageSearchLog, UUID> {

    // --- User search history ---
    List<ImageSearchLog> findByUserIdOrderBySearchedAtDesc(UUID userId);
    long countByUserId(UUID userId);

    // --- Lookup by Cloudinary public ID (for cleanup) ---
    List<ImageSearchLog> findByImagePublicId(String imagePublicId);

    // --- Check if an image was already searched ---
    boolean existsByImagePublicIdAndUserId(String imagePublicId, UUID userId);

    // --- Recent searches across platform (admin analytics) ---
    List<ImageSearchLog> findBySearchedAtAfterOrderBySearchedAtDesc(LocalDateTime since);

    // --- Searches that matched a specific product ---
    @Query("""
            SELECT isl FROM ImageSearchLog isl
            JOIN isl.matchedProducts mp
            WHERE mp.id = :productId
            ORDER BY isl.searchedAt DESC
            """)
    List<ImageSearchLog> findByMatchedProductId(UUID productId);

    // --- Most searched images (AI description analytics) ---
    @Query("""
            SELECT isl.aiDescription, COUNT(isl) as searchCount
            FROM ImageSearchLog isl
            WHERE isl.searchedAt BETWEEN :from AND :to
            GROUP BY isl.aiDescription
            ORDER BY searchCount DESC
            """)
    List<Object[]> findMostSearchedDescriptions(LocalDateTime from, LocalDateTime to);

    // --- User search count in a date range ---
    @Query("""
            SELECT COUNT(isl) FROM ImageSearchLog isl
            WHERE isl.user.id = :userId
            AND isl.searchedAt BETWEEN :from AND :to
            """)
    long countUserSearchesInRange(UUID userId, LocalDateTime from, LocalDateTime to);

    // --- Cleanup: delete old logs ---
    @Modifying
    @Transactional
    void deleteBySearchedAtBefore(LocalDateTime cutoff);

    // --- Cleanup: delete all logs for a user (GDPR/account deletion) ---
    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);
}