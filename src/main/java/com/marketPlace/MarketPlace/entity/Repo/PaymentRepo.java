package com.marketPlace.MarketPlace.entity.Repo;
 
import com.marketPlace.MarketPlace.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
import java.util.Optional;
import java.util.UUID;
 
@Repository
public interface PaymentRepo extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByPaystackReference(String reference);
    boolean existsByPaystackReference(String reference);
}