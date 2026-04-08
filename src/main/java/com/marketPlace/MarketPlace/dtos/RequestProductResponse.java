package com.marketPlace.MarketPlace.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RequestProductResponse {
    private UUID userId;
    private String name;
    private String email;
    private UserProductResponse productResponse;
    private ProductRequestPayload payload;
}
