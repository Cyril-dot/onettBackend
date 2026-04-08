package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
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
public interface UserProductRepository extends JpaRepository<UserProduct, UUID> {

    // Find by approval status (for admin review)
    Page<UserProduct> findByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);


    Optional<UserProduct> findByIdAndUserId(UUID id, UUID userId);

    // Check if a product belongs to a user
    boolean existsByIdAndUser(UUID id, User user);

    Optional<UserProduct> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<UserProduct> findByUserIdAndApprovalStatus(UUID userId, ApprovalStatus status, Pageable pageable);

    // Count products by user
    long countByUser(User user);

    // Count by approval status
    long countByApprovalStatus(ApprovalStatus approvalStatus);
}