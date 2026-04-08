package com.marketPlace.MarketPlace.dtos;
 
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID          id;
    private String        title;
    private String        message;
    private String        type;         // ORDER_CONFIRMED, PAYMENT_FAILED, etc.
    private String        referenceId;  // orderId or conversationId
    private boolean       isRead;
    private LocalDateTime createdAt;
}