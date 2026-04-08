package com.marketPlace.MarketPlace.entity.Enums;

public enum OrderStatus {
    AWAITING_PAYMENT,
    PAYMENT_FAILED,
    DEPOSIT_PAID,       // Pre-order: 50% paid, awaiting product availability + 2nd payment
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}