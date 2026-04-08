package com.marketPlace.MarketPlace.entity;

import com.marketPlace.MarketPlace.entity.Enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "seller")
public class Seller {
    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = GenerationType.AUTO)
    private java.util.UUID id;

    private String fullName;

    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.SELLER;

    private String phoneNumber;

    @OneToOne(cascade = CascadeType.ALL) // Cascades operations (save, delete, etc.)
    @JoinColumn(name = "profile_pic_id", referencedColumnName = "id") // Defines the foreign key column name
    private ProfilePic profilePic;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String sellerBio;


    private String storeName;

    @Column(columnDefinition = "TEXT")
    private String storeDescription;

    private String businessAddress;

    private String taxId;

    @Builder.Default
    private boolean isVerified = true;

    private String fcmToken;

    @Builder.Default
    @OneToMany(mappedBy = "seller", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductCategory> productCategories = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private java.time.LocalDateTime updatedAt;
}
