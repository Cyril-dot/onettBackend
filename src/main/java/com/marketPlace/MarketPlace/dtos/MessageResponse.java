package com.marketPlace.MarketPlace.dtos;
 
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private UUID          id;
    private UUID          conversationId;
    private String        senderType;
    private String        senderName;
    private String        content;
    private boolean       isProductCard;
    private boolean       isAutomated;      // NEW — true for system messages
    private String        productImageUrl;
    private boolean       isRead;
    private LocalDateTime createdAt;
    private LocalDateTime repliedAt;
}