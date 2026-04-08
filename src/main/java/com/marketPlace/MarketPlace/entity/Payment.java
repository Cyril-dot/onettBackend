package com.marketPlace.MarketPlace.entity;
 
import com.marketPlace.MarketPlace.entity.Enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
 
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
 
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
 
    @Column(name = "paystack_reference", nullable = false, unique = true, length = 100)
    private String paystackReference;
 
    @Column(name = "authorization_url", length = 500)
    private String authorizationUrl;
 
    @Column(name = "access_code", length = 200)
    private String accessCode;
 
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;
 
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
 
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
 
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}