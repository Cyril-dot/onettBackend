package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.ProfilePic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfilePicImageRepo extends JpaRepository<ProfilePic, Long> {

    // Lookup by Cloudinary public ID (useful for updates/deletions)
    Optional<ProfilePic> findByImagePublicId(String imagePublicId);

    // Check if a public ID already exists (avoid duplicate uploads)
    boolean existsByImagePublicId(String imagePublicId);

    // Lookup by image URL
    Optional<ProfilePic> findByImageUrl(String imageUrl);
}