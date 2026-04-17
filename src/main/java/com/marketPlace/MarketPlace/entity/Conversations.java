package com.marketPlace.MarketPlace.entity;

import com.marketPlace.MarketPlace.entity.Enums.ChatType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Table(name = "conversations", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_seller_id", columnList = "seller_id"),
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_order_id", columnList = "order_id")
})
public class Conversations {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    private ChatType chatType;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}