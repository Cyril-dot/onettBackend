package com.marketPlace.MarketPlace.dtos;
 
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID                   orderId;
    private String                 customerName;
    private String                 customerEmail;
    private String                 orderStatus;
    private String                 paymentStatus;      // NEW
    private String                 paymentReference;   // NEW
    private BigDecimal             total;
    private String                 deliveryAddress;
    private String                 notes;
    private boolean                canCancel;          // NEW — based on 2hr window
    private List<OrderItemResponse> orderItems;
    private LocalDateTime          createdAt;
    private LocalDateTime          updatedAt;
}