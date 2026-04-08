package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerRepo extends JpaRepository<Seller, UUID> {

    // Auth lookups
    Optional<Seller> findByEmail(String email);
    Optional<Seller> findByPhoneNumber(String phoneNumber);

    // Existence checks (registration validation)
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByTaxId(String taxId);
    boolean existsByStoreName(String storeName);

    // Store discovery
    Optional<Seller> findByStoreName(String storeName);
    List<Seller> findByStoreNameContainingIgnoreCase(String keyword);

    // Admin management
    List<Seller> findByIsVerified(boolean isVerified);
    List<Seller> findByLocation(String location);

    // Find sellers by product category name
    @Query("SELECT DISTINCT s FROM Seller s JOIN s.productCategories pc WHERE LOWER(pc.name) = LOWER(:categoryName)")
    List<Seller> findByCategoryName(String categoryName);
}