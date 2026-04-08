package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepo extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT u FROM User u WHERE u.fcmToken IS NOT NULL AND u.fcmToken <> ''")
    List<User> findAllUsersWithValidFcmToken();
}