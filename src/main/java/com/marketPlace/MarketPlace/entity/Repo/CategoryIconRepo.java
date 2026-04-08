package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.CategoryIcon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryIconRepo extends JpaRepository<CategoryIcon, Long> {

    // Lookup by Cloudinary public ID (for updates/deletions)
    Optional<CategoryIcon> findByImagePublicId(String imagePublicId);

    // Lookup by image URL
    Optional<CategoryIcon> findByImageUrl(String imageUrl);

    // Guard against duplicate Cloudinary uploads
    boolean existsByImagePublicId(String imagePublicId);

    // Guard against duplicate image URLs
    boolean existsByImageUrl(String imageUrl);
}