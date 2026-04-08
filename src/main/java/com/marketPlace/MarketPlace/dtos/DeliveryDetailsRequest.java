package com.marketPlace.MarketPlace.dtos;
 
import jakarta.validation.constraints.NotBlank;
import lombok.*;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryDetailsRequest {
 
    @NotBlank(message = "Full name is required")
    private String fullName;
 
    @NotBlank(message = "Email is required")
    private String email;
 
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
 
    @NotBlank(message = "WhatsApp number is required")
    private String whatsAppNumber;
 
    @NotBlank(message = "Landmark is required")
    private String landmark;
 
    @NotBlank(message = "Location/area is required")
    private String location;
 
    private String gpsAddress;  // optional — Ghana Post GPS or Google Maps pin
}