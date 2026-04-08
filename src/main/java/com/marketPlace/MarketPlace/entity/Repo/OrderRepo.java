package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Order;
import com.marketPlace.MarketPlace.entity.Enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepo extends JpaRepository<Order, UUID> {

    // ═══════════════════════════════════════════════════════════
    // USER — ORDER HISTORY
    // ═══════════════════════════════════════════════════════════

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Order> findByUserIdAndOrderStatus(UUID userId, OrderStatus status);

    Optional<Order> findByIdAndUserId(UUID orderId, UUID userId);

    // ═══════════════════════════════════════════════════════════
    // STATUS MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    List<Order> findByOrderStatus(OrderStatus status);

    long countByOrderStatus(OrderStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.orderStatus = :status, o.updatedAt = :updatedAt WHERE o.id = :orderId")
    void updateOrderStatus(@Param("orderId")   UUID orderId,
                           @Param("status")    OrderStatus status,
                           @Param("updatedAt") LocalDateTime updatedAt);

    // ═══════════════════════════════════════════════════════════
    // ADMIN — DATE RANGE QUERIES
    // ═══════════════════════════════════════════════════════════

    @Query("""
            SELECT o FROM Order o
            WHERE o.createdAt BETWEEN :from AND :to
            ORDER BY o.createdAt DESC
            """)
    List<Order> findOrdersByDateRange(@Param("from") LocalDateTime from,
                                      @Param("to")   LocalDateTime to);

    @Query("""
            SELECT o FROM Order o
            WHERE o.orderStatus = :status
              AND o.createdAt BETWEEN :from AND :to
            ORDER BY o.createdAt DESC
            """)
    List<Order> findOrdersByStatusAndDateRange(@Param("status") OrderStatus status,
                                               @Param("from")   LocalDateTime from,
                                               @Param("to")     LocalDateTime to);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :from AND :to")
    long countOrdersInDateRange(@Param("from") LocalDateTime from,
                                @Param("to")   LocalDateTime to);

    // ═══════════════════════════════════════════════════════════
    // SELLER — ORDERS CONTAINING THEIR PRODUCTS
    // ═══════════════════════════════════════════════════════════

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN o.orderItems oi
            WHERE oi.product.seller.id = :sellerId
            ORDER BY o.createdAt DESC
            """)
    List<Order> findOrdersBySellerId(@Param("sellerId") UUID sellerId);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN o.orderItems oi
            WHERE oi.product.seller.id = :sellerId
              AND o.orderStatus        = :status
            ORDER BY o.createdAt DESC
            """)
    List<Order> findOrdersBySellerIdAndStatus(@Param("sellerId") UUID sellerId,
                                              @Param("status")   OrderStatus status);

    // ═══════════════════════════════════════════════════════════
    // REVENUE ANALYTICS
    // ═══════════════════════════════════════════════════════════

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.orderStatus = :status")
    BigDecimal calculateTotalRevenueByStatus(@Param("status") OrderStatus status);

    @Query("""
            SELECT SUM(o.total) FROM Order o
            WHERE o.orderStatus = :status
              AND o.createdAt BETWEEN :from AND :to
            """)
    BigDecimal calculateRevenueByStatusAndDateRange(@Param("status") OrderStatus status,
                                                    @Param("from")   LocalDateTime from,
                                                    @Param("to")     LocalDateTime to);

    @Query("SELECT AVG(o.total) FROM Order o WHERE o.orderStatus = :status")
    BigDecimal calculateAverageOrderValue(@Param("status") OrderStatus status);
}