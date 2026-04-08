package com.marketPlace.MarketPlace.dtos;
 
import jakarta.validation.constraints.NotBlank;
import lombok.*;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenRequest {
 
    @NotBlank(message = "FCM token is required")
    private String fcmToken;
}