package com.marketPlace.MarketPlace.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UpdateUserProductRequest {

    private String name;

    private String productDescription;

    private String brand;

    private List<Long> imageIdsToDelete; // IDs of images to remove

}
