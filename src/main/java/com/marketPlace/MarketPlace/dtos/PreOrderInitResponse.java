package com.marketPlace.MarketPlace.dtos;
 
import lombok.*;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
 
// ─── Response returned when a pre-order deposit is initialized ───────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PreOrderInitResponse {
    private UUID preOrderRecordId;
    private UUID orderId;
    private String productName;
    private BigDecimal totalAmount;
    private BigDecimal depositAmount;       // 50% — to be paid now
    private BigDecimal remainingAmount;     // 50% — to be paid later
    private String authorizationUrl;        // Paystack checkout URL
    private String accessCode;
    private String reference;
    private String message;
}