package com.marketPlace.MarketPlace.entity.Enums;

public enum PreOrderStatus {

    /**
     * User has paid 50% deposit. Product is still COMING_SOON or PRE_ORDER.
     */
    DEPOSIT_PAID,

    /**
     * Product has been set to IN_STOCK by seller.
     * User has been notified and can now request delivery.
     */
    NOTIFIED,

    /**
     * User has clicked "Request Delivery" — they want to complete the purchase.
     * Admin needs to collect the remaining 50% manually and confirm.
     */
    DELIVERY_REQUESTED,

    /**
     * Admin has confirmed the 2nd manual payment.
     * Order will now be fully confirmed and flow proceeds normally.
     */
    COMPLETED,

    /**
     * User or admin cancelled before the order was completed.
     */
    CANCELLED
}