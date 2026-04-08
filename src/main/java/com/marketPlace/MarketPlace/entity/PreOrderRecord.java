package com.marketPlace.MarketPlace.entity;

import com.marketPlace.MarketPlace.entity.Enums.PreOrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pre_order_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreOrderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The parent order (still exists, still has status AWAITING_PAYMENT until full payment)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The product this pre-order is for
    // (we store it directly for easy querying by product)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private BigDecimal totalAmount;         // Full order total

    @Column(nullable = false)
    private BigDecimal depositAmount;       // 50% — paid via Paystack

    @Column(nullable = false)
    private BigDecimal remainingAmount;     // 50% — paid manually

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreOrderStatus status;          // DEPOSIT_PAID → NOTIFIED → DELIVERY_REQUESTED → COMPLETED

    // Set when admin manually confirms the 2nd payment
    private LocalDateTime secondPaymentConfirmedAt;

    // Admin who confirmed the 2nd payment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_admin_id")
    private User confirmedByAdmin;

    // Optional note from admin when confirming 2nd payment
    @Column(length = 500)
    private String adminNote;

    // Set when the product goes IN_STOCK and user is notified
    private LocalDateTime notifiedAt;

    // Set when user clicks "Request Delivery"
    private LocalDateTime deliveryRequestedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}