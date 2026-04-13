package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.OrderStatus;
import com.marketPlace.MarketPlace.entity.Enums.PaymentStatus;
import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import com.marketPlace.MarketPlace.entity.Repo.*;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final PaymentRepo         paymentRepo;
    private final OrderRepo           orderRepo;
    private final OrderService        orderService;
    private final PreOrderService     preOrderService;
    private final ChatService         chatService;
    private final CloudinaryService   cloudinaryService;
    private final NotificationService notificationService;

    // ═══════════════════════════════════════════════════════════
    // SUBMIT PAYMENT (customer uploads screenshot + sender info)
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public PaymentSubmitResponse submitPayment(UUID orderId, UUID userId,
                                               String senderAccountName,
                                               String senderPhoneNumber,
                                               MultipartFile screenshot) {

        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getOrderStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new ApiException("Order is not in a payable state: " + order.getOrderStatus());
        }

        // Idempotency — don't allow duplicate submission
        paymentRepo.findByOrderId(orderId).ifPresent(existing -> {
            if (existing.getPaymentStatus() == PaymentStatus.PENDING ||
                    existing.getPaymentStatus() == PaymentStatus.CONFIRMED) {
                throw new ApiException("Payment already submitted for this order.");
            }
        });

        boolean isPreOrder = isPreOrderOrder(order);

        BigDecimal chargeAmount = isPreOrder
                ? order.getTotal().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                : order.getTotal();

        BigDecimal remainingAmount = isPreOrder
                ? order.getTotal().subtract(chargeAmount)
                : null;

        // ── Upload screenshot to Cloudinary ───────────────────────────────────
        String screenshotUrl;
        String screenshotPublicId;
        try {
            var uploadResult   = cloudinaryService.uploadPaymentScreenshot(screenshot);
            screenshotUrl      = (String) uploadResult.get("secure_url");
            screenshotPublicId = (String) uploadResult.get("public_id");
        } catch (Exception e) {
            log.error("Cloudinary upload failed for order [{}]: {}", orderId, e.getMessage());
            throw new ApiException("Screenshot upload failed — please try again");
        }

        // ── Persist payment record ────────────────────────────────────────────
        Payment payment = Payment.builder()
                .order(order)
                .amount(chargeAmount)
                .senderAccountName(senderAccountName)
                .senderPhoneNumber(senderPhoneNumber)
                .screenshotUrl(screenshotUrl)
                .screenshotPublicId(screenshotPublicId)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        paymentRepo.save(payment);

        // ── Notify user (submitted) + notify all admins ───────────────────────
        notificationService.notifyAdminPaymentSubmitted(
                order, senderAccountName, senderPhoneNumber, chargeAmount, isPreOrder);
        notificationService.notifyAdminSellerOrderPlaced(order);

        log.info("Payment submitted for order [{}] — sender: {} — amount: {} GHS — preOrder: {}",
                orderId, senderAccountName, chargeAmount, isPreOrder);

        return PaymentSubmitResponse.builder()
                .orderId(orderId)
                .paymentStatus(PaymentStatus.PENDING.name())
                .amount(chargeAmount)
                .isPreOrder(isPreOrder)
                .depositAmount(isPreOrder ? chargeAmount : null)
                .remainingAmount(remainingAmount)
                .screenshotUrl(screenshotUrl)
                .message("Payment submitted successfully. Awaiting admin confirmation.")
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — VIEW ALL PAYMENTS (with order + user details)
    // ═══════════════════════════════════════════════════════════

    public List<PaymentAdminResponse> getAllPayments() {
        return paymentRepo.findAll().stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — VIEW PAYMENTS BY STATUS
    // ═══════════════════════════════════════════════════════════

    public List<PaymentAdminResponse> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepo.findByPaymentStatus(status).stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — VIEW SINGLE PAYMENT BY ORDER ID
    // ═══════════════════════════════════════════════════════════

    public PaymentAdminResponse getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for order: " + orderId));
        return mapToAdminResponse(payment);
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — VIEW SINGLE PAYMENT BY PAYMENT ID
    // ═══════════════════════════════════════════════════════════

    public PaymentAdminResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + paymentId));
        return mapToAdminResponse(payment);
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — CONFIRM PAYMENT
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void confirmPayment(UUID orderId, String adminNote) {
        Payment payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for order: " + orderId));

        if (payment.getPaymentStatus() == PaymentStatus.CONFIRMED) {
            log.info("Payment for order [{}] already CONFIRMED — skipping", orderId);
            return;
        }

        payment.setPaymentStatus(PaymentStatus.CONFIRMED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setAdminNote(adminNote);
        paymentRepo.save(payment);

        Order order       = payment.getOrder();
        boolean isPreOrder = isPreOrderOrder(order);

        if (isPreOrder) {
            log.info("Payment CONFIRMED (deposit) for pre-order [{}] — routing to PreOrderService",
                    orderId);
            preOrderService.handleDepositPaid(orderId);
        } else {
            log.info("Payment CONFIRMED for order [{}] — confirming order", orderId);
            orderService.confirmOrderAfterPayment(orderId);
            chatService.createOrderChat(orderId);
            notificationService.notifyOrderConfirmed(order);
            notificationService.notifySellerPaymentReceived(order);
        }

        log.info("Payment confirmation complete for order [{}] — preOrder: {}",
                orderId, isPreOrder);
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — REJECT PAYMENT
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void rejectPayment(UUID orderId, String adminNote) {
        Payment payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for order: " + orderId));

        if (payment.getPaymentStatus() == PaymentStatus.REJECTED) {
            log.info("Payment for order [{}] already REJECTED — skipping", orderId);
            return;
        }

        payment.setPaymentStatus(PaymentStatus.REJECTED);
        payment.setAdminNote(adminNote);
        paymentRepo.save(payment);

        Order order = payment.getOrder();
        orderService.markOrderPaymentFailed(orderId);
        notificationService.notifyUserPaymentRejected(order, adminNote);
        notificationService.notifyPaymentFailed(order);

        log.warn("Payment REJECTED for order [{}] — reason: {}", orderId, adminNote);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — PRE-ORDER DETECTION
    // ═══════════════════════════════════════════════════════════

    private boolean isPreOrderOrder(Order order) {
        return order.getOrderItems().stream()
                .anyMatch(item ->
                        item.getProduct().getStockStatus() == StockStatus.PRE_ORDER
                                || item.getProduct().getStockStatus() == StockStatus.COMING_SOON);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — MAPPER
    // ═══════════════════════════════════════════════════════════

    private PaymentAdminResponse mapToAdminResponse(Payment p) {
        Order order = p.getOrder();
        return PaymentAdminResponse.builder()
                .paymentId(p.getId())
                .orderId(order.getId())
                .userId(order.getUser().getId())
                .userFullName(order.getUser().getFullName())
                .userEmail(order.getUser().getEmail())
                .amount(p.getAmount())
                .orderTotal(order.getTotal())
                .isPreOrder(isPreOrderOrder(order))
                .senderAccountName(p.getSenderAccountName())
                .senderPhoneNumber(p.getSenderPhoneNumber())
                .screenshotUrl(p.getScreenshotUrl())
                .paymentStatus(p.getPaymentStatus())
                .adminNote(p.getAdminNote())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .orderItems(order.getOrderItems().stream()
                        .map(i -> OrderItemSummary.builder()
                                .productId(i.getProduct().getId())
                                .productName(i.getProduct().getName())
                                .quantity(i.getQuantity())
                                .subTotal(i.getSubTotal())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}