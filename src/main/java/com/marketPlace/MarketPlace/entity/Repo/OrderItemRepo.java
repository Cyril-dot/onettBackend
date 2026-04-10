package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepo extends JpaRepository<OrderItem, UUID> {

    // --- Order-based queries ---
    List<OrderItem> findByOrderId(UUID orderId);
    long countByOrderId(UUID orderId);

    // --- Product-based queries ---
    List<OrderItem> findByProductId(UUID productId);
    long countByProductId(UUID productId);

    // --- Seller analytics: all items sold by a seller's products ---
    @Query("SELECT oi FROM OrderItem oi WHERE oi.product.seller.id = :sellerId")
    List<OrderItem> findByProductSellerId(UUID sellerId);

    // --- Revenue: total revenue for a specific product ---
    @Query("SELECT SUM(oi.subTotal) FROM OrderItem oi WHERE oi.product.id = :productId")
    BigDecimal calculateRevenueByProduct(UUID productId);

    // --- Revenue: total revenue for a seller ---
    @Query("SELECT SUM(oi.subTotal) FROM OrderItem oi WHERE oi.product.seller.id = :sellerId")
    BigDecimal calculateRevenueBySeller(UUID sellerId);

    // --- Revenue: total revenue for a seller within a date range ---
    @Query("""
            SELECT SUM(oi.subTotal) FROM OrderItem oi
            WHERE oi.product.seller.id = :sellerId
            AND oi.orderedAt BETWEEN :from AND :to
            """)
    BigDecimal calculateRevenueBySellerAndDateRange(UUID sellerId, LocalDateTime from, LocalDateTime to);

    // --- Best selling products by quantity ---
    @Query("""
            SELECT oi.product.id, SUM(oi.quantity) as totalSold
            FROM OrderItem oi
            GROUP BY oi.product.id
            ORDER BY totalSold DESC
            """)
    List<Object[]> findBestSellingProducts();

    @Modifying
    @Query("UPDATE OrderItem oi SET oi.product = null WHERE oi.product.id = :productId")
    void nullifyProductReference(@Param("productId") UUID productId);

    // --- Best selling products for a specific seller ---
    @Query("""
            SELECT oi.product.id, oi.product.name, SUM(oi.quantity) as totalSold
            FROM OrderItem oi
            WHERE oi.product.seller.id = :sellerId
            GROUP BY oi.product.id, oi.product.name
            ORDER BY totalSold DESC
            """)
    List<Object[]> findBestSellingProductsBySeller(UUID sellerId);

    // --- Total units sold for a product ---
    @Query("SELECT SUM(oi.quantity) FROM OrderItem oi WHERE oi.product.id = :productId")
    Integer totalUnitsSoldByProduct(UUID productId);
}