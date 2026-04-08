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
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_table")
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String fullName;

    @Column(unique = true)
    private String email;

    private String password;

    @Column(unique = true)
    private String phoneNumber;

    @OneToOne(cascade = CascadeType.ALL) // Cascades operations (save, delete, etc.)
    @JoinColumn(name = "profile_pic_id", referencedColumnName = "id") // Defines the foreign key column name
    private ProfilePic profilePic;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // In User.java — add this field alongside the others
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();
}
