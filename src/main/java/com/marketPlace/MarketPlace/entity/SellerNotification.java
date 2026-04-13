package com.marketPlace.MarketPlace.entity;


import com.marketPlace.MarketPlace.entity.Enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seller_notifications")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SellerNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String referenceId;

    @Builder.Default
    private boolean read = false;

    @CreationTimestamp
    private Instant createdAt;
}