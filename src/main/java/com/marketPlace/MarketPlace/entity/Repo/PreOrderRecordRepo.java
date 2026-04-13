package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.PreOrderRecord;
import com.marketPlace.MarketPlace.entity.Enums.PreOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreOrderRecordRepo extends JpaRepository<PreOrderRecord, UUID> {


    List<PreOrderRecord> findByUserId(UUID userId);

    List<PreOrderRecord> findByProductIdAndStatus(UUID productId, PreOrderStatus status);

    List<PreOrderRecord> findByStatus(PreOrderStatus status);

    @Modifying
    @Query("UPDATE PreOrderRecord p SET p.product = null WHERE p.product.id = :productId")
    void nullifyProductReference(@Param("productId") UUID productId);

    // Admin: all pre-order records across all products, joined with user info
    @Query("SELECT p FROM PreOrderRecord p " +
           "JOIN FETCH p.user " +
           "JOIN FETCH p.product " +
           "WHERE p.status IN :statuses " +
           "ORDER BY p.createdAt DESC")
    List<PreOrderRecord> findAllActivePreOrders(
            @Param("statuses") List<PreOrderStatus> statuses);

    // Admin: pre-orders for a specific product
    @Query("SELECT p FROM PreOrderRecord p " +
           "JOIN FETCH p.user " +
           "WHERE p.product.id = :productId " +
           "ORDER BY p.createdAt DESC")
    List<PreOrderRecord> findByProductIdWithUser(@Param("productId") UUID productId);

    boolean existsByOrderId(UUID orderId);
}