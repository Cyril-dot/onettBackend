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

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // --- Sender Info ---

    @Column(name = "sender_account_name", nullable = false, length = 150)
    private String senderAccountName;

    @Column(name = "sender_phone_number", nullable = false, length = 20)
    private String senderPhoneNumber;

    // --- Payment Proof (Cloudinary) ---

    @Column(name = "screenshot_url", nullable = false, length = 500)
    private String screenshotUrl;

    @Column(name = "screenshot_public_id", nullable = false, length = 200)
    private String screenshotPublicId;

    // --- Status & Verification ---

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "admin_note", length = 300)
    private String adminNote;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}