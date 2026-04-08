package com.marketPlace.MarketPlace.Config.Security.entity;

import com.marketPlace.MarketPlace.entity.Seller;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Table(name = "admin_tokens")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
public class AdminRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true, columnDefinition = "VARCHAR(1000)")
    private String token;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", nullable = false, unique = true, columnDefinition = "uuid")
    private Seller seller;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;
}