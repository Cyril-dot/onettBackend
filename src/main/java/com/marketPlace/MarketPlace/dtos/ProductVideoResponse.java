package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class ProductVideoResponse {
    private UUID id;
    private String videoUrl;
    private String thumbnailUrl;
    private String cloudinaryPublicId;
    private String originalFileName;
    private String format;
    private Long fileSizeBytes;
    private Integer durationSeconds;
    private Integer width;
    private Integer height;
    private Boolean isPrimary;
}