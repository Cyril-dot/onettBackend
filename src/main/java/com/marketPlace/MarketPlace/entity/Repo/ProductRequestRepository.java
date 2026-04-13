package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import com.marketPlace.MarketPlace.entity.ProductRequest;
import com.marketPlace.MarketPlace.entity.User;
import com.marketPlace.MarketPlace.entity.UserProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRequestRepository extends JpaRepository<ProductRequest, UUID> {

    // Find all requests by a user
    Page<ProductRequest> findByUser(User user, Pageable pageable);

    Page<ProductRequest> findByUserId(UUID userId, Pageable pageable);

    // Find all requests for a specific product
    List<ProductRequest> findByUserProduct(UserProduct userProduct);

    List<ProductRequest> findByUserProductId(UUID userProductId);

    // Find a specific request by user and product
    Optional<ProductRequest> findByUserAndUserProduct(User user, UserProduct userProduct);

    // Check if a user has already requested a product
    boolean existsByUserAndUserProduct(User user, UserProduct userProduct);

    // Find by approval status (for admin)
    Page<ProductRequest> findByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);

    // Find paid/unpaid requests
    Page<ProductRequest> findByUserAndPaid(User user, Boolean paid, Pageable pageable);

    Page<ProductRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Find pending payment requests (unpaid)
    @Query("""
            SELECT r FROM ProductRequest r
            WHERE r.user = :user
            AND r.paid = false
            AND r.approvalStatus = 'PENDING'
            """)
    List<ProductRequest> findUnpaidRequestsByUser(@Param("user") User user);



    Optional<ProductRequest> findByIdAndUserId(UUID id, UUID userId);

    Optional<ProductRequest> findById(UUID id);


    // Count requests per product
    long countByUserProduct(UserProduct userProduct);

    // Count by approval status (for admin dashboard)
    long countByApprovalStatus(ApprovalStatus approvalStatus);
}