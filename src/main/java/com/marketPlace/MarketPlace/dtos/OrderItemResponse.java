// ═══════════════════════════════════════════════════════════════════
package com.marketPlace.MarketPlace.dtos;
 
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private UUID          id;
    private UUID          productId;
    private String        productName;
    private String        primaryImageUrl;
    private int           quantity;
    private BigDecimal    unitPrice;
    private BigDecimal    subTotal;
    private LocalDateTime orderedAt;
}