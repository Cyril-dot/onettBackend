package com.marketPlace.MarketPlace.entity.Enums;

public enum PaymentStatus {
    PENDING,    // Customer submitted screenshot, awaiting admin review
    CONFIRMED,  // Admin verified and approved the payment
    REJECTED    // Admin rejected (e.g. wrong amount, fake/unclear screenshot)
}