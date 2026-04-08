package com.marketPlace.MarketPlace.dtos;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class ConfirmSecondPaymentRequest {
    private String adminNote;               // Optional note, e.g. "Paid via MoMo — ref XYZ123"
}