package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.OrderStatus;
import com.marketPlace.MarketPlace.entity.Enums.PreOrderStatus;
import com.marketPlace.MarketPlace.entity.Repo.*;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreOrderService {

    private final PreOrderRecordRepo    preOrderRecordRepo;
    private final OrderRepo             orderRepo;
    private final ProductRepo           productRepo;
    private final UserRepo              userRepo;
    private final CartRepo              cartRepo;
    private final NotificationService   notificationService;
    private final ChatService           chatService;

    // ═══════════════════════════════════════════════════════════════════════
    // CALLED BY OrderPaymentService after deposit payment confirmed
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void handleDepositPaid(UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (preOrderRecordRepo.existsByOrderId(orderId)) {
            log.info("PreOrderRecord already exists for order [{}] — skipping (idempotent)", orderId);
            return;
        }

        OrderItem item = order.getOrderItems().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException("Order has no items: " + orderId));

        Product product = item.getProduct();

        BigDecimal total     = order.getTotal();
        BigDecimal deposit   = total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal remaining = total.subtract(deposit);

        PreOrderRecord record = PreOrderRecord.builder()
                .order(order)
                .user(order.getUser())
                .product(product)
                .totalAmount(total)
                .depositAmount(deposit)
                .remainingAmount(remaining)
                .status(PreOrderStatus.DEPOSIT_PAID)
                .build();

        preOrderRecordRepo.save(record);

        orderRepo.updateOrderStatus(orderId, OrderStatus.DEPOSIT_PAID, LocalDateTime.now());
        order.setOrderStatus(OrderStatus.DEPOSIT_PAID);

        cartRepo.deleteByUserId(order.getUser().getId());

        log.info("PreOrderRecord created for order [{}] — product [{}] — deposit: {} — remaining: {}",
                orderId, product.getName(), deposit, remaining);

        chatService.createOrderChat(orderId);
        notificationService.notifyDepositConfirmed(order, deposit, remaining);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CALLED BY ProductService when seller sets stockStatus to IN_STOCK
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void onProductInStock(UUID productId) {
        List<PreOrderRecord> waitingRecords = preOrderRecordRepo
                .findByProductIdAndStatus(productId, PreOrderStatus.DEPOSIT_PAID);

        if (waitingRecords.isEmpty()) {
            log.info("No waiting pre-order records for product [{}]", productId);
            return;
        }

        log.info("Product [{}] is now IN_STOCK — notifying {} pre-order customer(s)",
                productId, waitingRecords.size());

        for (PreOrderRecord record : waitingRecords) {
            record.setStatus(PreOrderStatus.NOTIFIED);
            record.setNotifiedAt(LocalDateTime.now());
            preOrderRecordRepo.save(record);

            notificationService.notifyPreOrderProductAvailable(record);

            log.info("Notified user [{}] that product [{}] is now available — preOrderRecord [{}]",
                    record.getUser().getEmail(), record.getProduct().getName(), record.getId());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // USER — REQUEST DELIVERY
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public PreOrderRecordResponse requestDelivery(UUID preOrderRecordId, UUID userId) {
        PreOrderRecord record = findRecordByIdAndUserId(preOrderRecordId, userId);

        if (record.getStatus() == PreOrderStatus.DELIVERY_REQUESTED) {
            log.info("Delivery already requested for preOrderRecord [{}]", preOrderRecordId);
            return mapToResponse(record);
        }
        if (record.getStatus() != PreOrderStatus.NOTIFIED) {
            throw new ApiException("Cannot request delivery — current status: " + record.getStatus()
                    + ". You must wait until the product is available.");
        }

        record.setStatus(PreOrderStatus.DELIVERY_REQUESTED);
        record.setDeliveryRequestedAt(LocalDateTime.now());
        preOrderRecordRepo.save(record);

        log.info("User [{}] requested delivery for preOrderRecord [{}] — product [{}]",
                userId, preOrderRecordId, record.getProduct().getName());

        notificationService.notifyAdminDeliveryRequested(record);

        return mapToResponse(record);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — CONFIRM 2ND PAYMENT (manual)
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public PreOrderRecordResponse confirmSecondPayment(UUID preOrderRecordId, UUID adminId, String adminNote) {
        PreOrderRecord record = preOrderRecordRepo.findById(preOrderRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("PreOrderRecord not found: " + preOrderRecordId));

        if (record.getStatus() == PreOrderStatus.COMPLETED) {
            log.info("PreOrderRecord [{}] already COMPLETED — skipping (idempotent)", preOrderRecordId);
            return mapToResponse(record);
        }
        if (record.getStatus() != PreOrderStatus.DELIVERY_REQUESTED) {
            throw new ApiException("Cannot confirm payment — status is: " + record.getStatus()
                    + ". User must request delivery first.");
        }

        User admin = userRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found: " + adminId));

        Product product = record.getProduct();
        if (product.getStock() < 1) {
            throw new ApiException("Product [" + product.getName() + "] is out of stock — "
                    + "cannot confirm delivery. Please contact the seller.");
        }

        OrderItem item = record.getOrder().getOrderItems().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException("No order items found"));

        int newStock = product.getStock() - item.getQuantity();
        if (newStock < 0) {
            throw new ApiException("Insufficient stock for [" + product.getName() + "] — "
                    + "available: " + product.getStock() + ", requested: " + item.getQuantity());
        }
        product.setStock(newStock);
        productRepo.save(product);

        log.info("[ADMIN] Stock deducted for product [{}]: -{} (remaining: {})",
                product.getName(), item.getQuantity(), newStock);

        record.setStatus(PreOrderStatus.COMPLETED);
        record.setSecondPaymentConfirmedAt(LocalDateTime.now());
        record.setConfirmedByAdmin(admin);
        record.setAdminNote(adminNote);
        preOrderRecordRepo.save(record);

        Order order = record.getOrder();
        orderRepo.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED, LocalDateTime.now());
        order.setOrderStatus(OrderStatus.CONFIRMED);

        log.info("[ADMIN] PreOrderRecord [{}] COMPLETED — order [{}] confirmed by admin [{}]",
                preOrderRecordId, order.getId(), admin.getEmail());

        notificationService.notifyPreOrderFullyConfirmed(record);

        return mapToResponse(record);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — QUERIES
    // ═══════════════════════════════════════════════════════════════════════

    public List<PreOrderRecordResponse> getAllActivePreOrders() {
        log.info("[ADMIN] Fetching all active pre-order records");
        return preOrderRecordRepo.findAllActivePreOrders(
                        List.of(PreOrderStatus.DEPOSIT_PAID,
                                PreOrderStatus.NOTIFIED,
                                PreOrderStatus.DELIVERY_REQUESTED))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<PreOrderRecordResponse> getPreOrdersByProduct(UUID productId) {
        log.info("[ADMIN] Fetching pre-order records for product [{}]", productId);
        return preOrderRecordRepo.findByProductIdWithUser(productId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<PreOrderRecordResponse> getPreOrdersByStatus(PreOrderStatus status) {
        log.info("[ADMIN] Fetching pre-orders with status [{}]", status);
        return preOrderRecordRepo.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // USER — VIEW OWN PRE-ORDERS
    // ═══════════════════════════════════════════════════════════════════════

    public List<PreOrderRecordResponse> getUserPreOrders(UUID userId) {
        log.info("Fetching pre-order records for user [{}]", userId);
        return preOrderRecordRepo.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private PreOrderRecord findRecordByIdAndUserId(UUID recordId, UUID userId) {
        return preOrderRecordRepo.findById(recordId)
                .filter(r -> r.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PreOrderRecord not found for user: " + recordId));
    }

    PreOrderRecordResponse mapToResponse(PreOrderRecord record) {
        return PreOrderRecordResponse.builder()
                .id(record.getId())
                .orderId(record.getOrder().getId())
                .userId(record.getUser().getId())
                .customerName(record.getUser().getFullName())
                .customerEmail(record.getUser().getEmail())
                .productId(record.getProduct().getId())
                .productName(record.getProduct().getName())
                .totalAmount(record.getTotalAmount())
                .depositAmount(record.getDepositAmount())
                .remainingAmount(record.getRemainingAmount())
                .status(record.getStatus().name())
                .notifiedAt(record.getNotifiedAt())
                .deliveryRequestedAt(record.getDeliveryRequestedAt())
                .secondPaymentConfirmedAt(record.getSecondPaymentConfirmedAt())
                .confirmedByAdminName(record.getConfirmedByAdmin() != null
                        ? record.getConfirmedByAdmin().getFullName() : null)
                .adminNote(record.getAdminNote())
                .createdAt(record.getCreatedAt())
                .build();
    }
}