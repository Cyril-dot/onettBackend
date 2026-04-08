package com.marketPlace.MarketPlace.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_video")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String cloudinaryPublicId;      // e.g. "marketplace/products/abc123"

    @Column(nullable = false)
    private String videoUrl;                // Secure delivery URL (https://res.cloudinary.com/...)

    private String thumbnailUrl;            // Auto-generated poster/thumbnail URL

    // --- Video Metadata ---

    private String originalFileName;        // Original file name before upload

    private String format;                  // e.g. "mp4", "webm", "mov"

    private Long fileSizeBytes;             // File size in bytes

    private Integer durationSeconds;        // Video length in seconds

    private Integer width;                  // Video width in pixels

    private Integer height;                 // Video height in pixels

    @Builder.Default
    private Integer displayOrder = 0;       // For ordering multiple videos on a product

    @Builder.Default
    private Boolean isPrimary = false;      // Flag for the main/featured video

    // --- Timestamps ---

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // In ProductVideo.java — replace or add alongside the UserProduct relation

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}