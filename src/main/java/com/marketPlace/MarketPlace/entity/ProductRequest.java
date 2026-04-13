package com.marketPlace.MarketPlace.entity;

import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Entity
@Table(name = "product_request")
public class ProductRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal amount = BigDecimal.valueOf(100.00);

    @Builder.Default
    private Boolean paid = false;

    // --- Sender Info (customer who made the MoMo payment) ---

    @Column(name = "sender_account_name", nullable = false, length = 150)
    private String senderAccountName;   // Name on the MoMo account that sent the money

    @Column(name = "sender_phone_number", nullable = false, length = 20)
    private String senderPhoneNumber;   // MoMo number that sent the money

    // --- Payment Proof (Cloudinary) ---

    @Column(name = "screenshot_url", nullable = false, length = 500)
    private String screenshotUrl;       // Cloudinary secure URL

    @Column(name = "screenshot_public_id", nullable = false, length = 200)
    private String screenshotPublicId;  // Cloudinary public_id (for deletion/replacement)

    // --- Admin Review ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "admin_note", length = 300)
    private String adminNote;           // Reason for rejection or confirmation note

    // --- Product (uploaded AFTER payment is confirmed) ---

    @OneToOne(mappedBy = "productRequest", fetch = FetchType.LAZY)
    private UserProduct userProduct;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}