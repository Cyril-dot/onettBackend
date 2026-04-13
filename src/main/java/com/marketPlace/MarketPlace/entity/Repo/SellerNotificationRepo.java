package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.SellerNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.*;

public interface SellerNotificationRepo
        extends JpaRepository<SellerNotification, UUID> {

    List<SellerNotification> findBySeller_IdOrderByCreatedAtDesc(UUID sellerId);

    List<SellerNotification> findBySeller_IdAndReadFalseOrderByCreatedAtDesc(UUID sellerId);

    Optional<SellerNotification> findByIdAndSeller_Id(UUID id, UUID sellerId);

    long countBySeller_IdAndReadFalse(UUID sellerId);

    @Modifying
    @Query("UPDATE SellerNotification n SET n.read = true WHERE n.seller.id = :sellerId AND n.read = false")
    int markAllAsReadBySellerId(UUID sellerId);
}