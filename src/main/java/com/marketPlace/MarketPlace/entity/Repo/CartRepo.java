package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepo extends JpaRepository<Cart, UUID> {

    // --- User cart ---
    List<Cart> findByUserId(UUID userId);
    long countByUserId(UUID userId);

    // --- Specific cart item lookup ---
    Optional<Cart> findByUserIdAndProductId(UUID userId, UUID productId);
    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    @Modifying
    @Query("DELETE FROM Cart c WHERE c.product.id = :productId")
    int deleteByProductId(@Param("productId") UUID productId);

    // --- Clear entire cart ---
    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);

    // --- Remove specific item from cart ---
    @Modifying
    @Transactional
    void deleteByUserIdAndProductId(UUID userId, UUID productId);

    // --- Update quantity ---
    @Modifying
    @Transactional
    @Query("UPDATE Cart c SET c.quantity = :quantity WHERE c.user.id = :userId AND c.product.id = :productId")
    void updateQuantity(UUID userId, UUID productId, Integer quantity);

    // --- Cart total value ---
    @Query("""
            SELECT SUM(c.quantity * p.price)
            FROM Cart c
            JOIN Product p ON p.id = c.product.id
            WHERE c.user.id = :userId
            """)
    BigDecimal calculateCartTotal(UUID userId);

    // --- Cart total with discounts applied ---
    @Query("""
        SELECT SUM(c.quantity *
            CASE WHEN p.discounted = true
            THEN p.discountPrice
            ELSE p.price END)
        FROM Cart c
        JOIN Product p ON p.id = c.product.id
        WHERE c.user.id = :userId
        """)
    BigDecimal calculateDiscountedCartTotal(UUID userId);

    // --- Find cart items for a specific seller's products ---
    @Query("""
            SELECT c FROM Cart c
            WHERE c.user.id = :userId
            AND c.product.seller.id = :sellerId
            """)
    List<Cart> findByUserIdAndSellerId(UUID userId, UUID sellerId);

    // --- Total items count (sum of quantities) ---
    @Query("SELECT SUM(c.quantity) FROM Cart c WHERE c.user.id = :userId")
    Integer totalItemsInCart(UUID userId);
}