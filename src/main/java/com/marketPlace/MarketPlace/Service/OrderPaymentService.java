package com.marketPlace.MarketPlace.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final PaymentRepo       paymentRepo;
    private final OrderRepo         orderRepo;
    private final OrderService      orderService;
    private final PreOrderService   preOrderService;
    private final ChatService       chatService;
    private final RestTemplate      restTemplate;
    private final ObjectMapper      objectMapper;

    @Value("${paystack.secret.key}")
    private String paystackSecretKey;

    @Value("${paystack.base.url:https://api.paystack.co}")
    private String paystackBaseUrl;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // ═══════════════════════════════════════════════════════════
    // INITIALIZE PAYMENT
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public PaymentInitResponse initializePayment(UUID orderId, UUID userId) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getOrderStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new ApiException("Order is not in a payable state: " + order.getOrderStatus());
        }

        // ── Detect pre-order upfront (needed for both idempotency and new payment) ──
        boolean isPreOrder = isPreOrderOrder(order);

        BigDecimal chargeAmount = isPreOrder
                ? order.getTotal().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                : order.getTotal();

        BigDecimal remainingAmount = isPreOrder
                ? order.getTotal().subtract(chargeAmount)
                : null;

        // ── Idempotency check — return existing PENDING payment if available ────────
        // FIX: now includes isPreOrder / depositAmount / remainingAmount so the
        //      frontend always gets complete data even on retry.
        Optional<Payment> existing = paymentRepo.findByOrderId(orderId);
        if (existing.isPresent()
                && existing.get().getAuthorizationUrl() != null
                && existing.get().getPaymentStatus() == PaymentStatus.PENDING) {

            log.info("Returning existing payment URL for order [{}]", orderId);

            return PaymentInitResponse.builder()
                    .authorizationUrl(existing.get().getAuthorizationUrl())
                    .accessCode(existing.get().getAccessCode())
                    .reference(existing.get().getPaystackReference())
                    .orderId(orderId)
                    .isPreOrder(isPreOrder)
                    .depositAmount(isPreOrder ? chargeAmount : null)
                    .remainingAmount(remainingAmount)
                    .build();
        }

        // ── Build Paystack payload ─────────────────────────────────────────────────
        String reference = generateReference(orderId);

        // FIX: use setScale + HALF_UP before longValue() to avoid silent truncation
        //      e.g. 149.999 pesewas → was 149, now correctly 150
        long amountInPesewas = chargeAmount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email",        order.getUser().getEmail());
        payload.put("amount",       amountInPesewas);
        payload.put("reference",    reference);
        payload.put("currency",     "GHS");
        payload.put("callback_url", frontendUrl + "/payment/callback");
        payload.put("metadata", Map.of(
                "order_id",      orderId.toString(),
                "is_pre_order",  String.valueOf(isPreOrder),
                "customer_name", order.getUser().getFullName(),
                "cancel_action", frontendUrl + "/payment/cancelled"
        ));

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        log.info("Initializing Paystack payment for order [{}] — ref: [{}] — amount: {} pesewas — preOrder: {}",
                orderId, reference, amountInPesewas, isPreOrder);

        // ── Call Paystack ──────────────────────────────────────────────────────────
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(
                    paystackBaseUrl + "/transaction/initialize",
                    requestEntity,
                    String.class
            );
        } catch (Exception e) {
            log.error("Paystack API call failed for order [{}]: {}", orderId, e.getMessage());
            throw new ApiException("Payment gateway error — please try again");
        }

        JsonNode json = parseResponse(response.getBody());
        if (!json.path("status").asBoolean()) {
            String msg = json.path("message").asText("Payment initialization failed");
            log.error("Paystack rejected payment init for order [{}]: {}", orderId, msg);
            throw new ApiException("Payment initialization failed: " + msg);
        }

        JsonNode data         = json.path("data");
        String authorizationUrl = data.path("authorization_url").asText();
        String accessCode       = data.path("access_code").asText();

        // ── Persist payment record ─────────────────────────────────────────────────
        Payment payment = Payment.builder()
                .order(order)
                .paystackReference(reference)
                .authorizationUrl(authorizationUrl)
                .accessCode(accessCode)
                .amount(chargeAmount)               // actual charge (50% for pre-orders)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        paymentRepo.save(payment);

        log.info("Payment initialized for order [{}] — charged: {} GHS — preOrder: {} — status: PENDING",
                orderId, chargeAmount, isPreOrder);

        return PaymentInitResponse.builder()
                .authorizationUrl(authorizationUrl)
                .accessCode(accessCode)
                .reference(reference)
                .orderId(orderId)
                .isPreOrder(isPreOrder)
                .depositAmount(isPreOrder ? chargeAmount : null)
                .remainingAmount(remainingAmount)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // WEBHOOK HANDLER
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void handleWebhook(String payload, String paystackSignature) {
        log.info("Paystack webhook received — verifying signature");

        if (!isValidSignature(payload, paystackSignature)) {
            log.error("Paystack webhook signature verification FAILED — ignoring request");
            throw new ApiException("Invalid webhook signature");
        }

        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("Failed to parse Paystack webhook payload: {}", e.getMessage());
            throw new ApiException("Invalid webhook payload");
        }

        String eventType = event.path("event").asText();
        log.info("Paystack webhook event type: [{}]", eventType);

        switch (eventType) {
            case "charge.success" -> handleChargeSuccess(event.path("data"));
            case "charge.failed"  -> handleChargeFailed(event.path("data"));
            default -> log.info("Unhandled Paystack event type: [{}] — skipping", eventType);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VERIFY PAYMENT  (polling fallback for callback page)
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public PaymentVerifyResponse verifyPayment(String reference) {
        Payment payment = paymentRepo.findByPaystackReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for reference: " + reference));

        // Already settled — return cached result without hitting Paystack again
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.info("Payment [{}] already verified as SUCCESS", reference);
            return buildVerifyResponse(payment, true);
        }
        if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
            log.info("Payment [{}] already verified as FAILED", reference);
            return buildVerifyResponse(payment, false);
        }

        // ── Ask Paystack ───────────────────────────────────────────────────────────
        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        log.info("Verifying payment reference [{}] with Paystack", reference);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    paystackBaseUrl + "/transaction/verify/" + reference,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );
        } catch (Exception e) {
            log.error("Paystack verify call failed for ref [{}]: {}", reference, e.getMessage());
            throw new ApiException("Payment verification failed — please try again");
        }

        JsonNode json   = parseResponse(response.getBody());
        String   status = json.path("data").path("status").asText();

        log.info("Paystack verification result for [{}]: status = [{}]", reference, status);

        if ("success".equalsIgnoreCase(status)) {
            processSuccessfulPayment(payment);
            return buildVerifyResponse(payment, true);
        } else {
            processFailedPayment(payment);
            return buildVerifyResponse(payment, false);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — WEBHOOK EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════

    private void handleChargeSuccess(JsonNode data) {
        String reference = data.path("reference").asText();
        log.info("Processing charge.success for reference [{}]", reference);

        paymentRepo.findByPaystackReference(reference).ifPresentOrElse(payment -> {
            if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                log.info("Payment [{}] already processed as SUCCESS — skipping (idempotent)", reference);
                return;
            }
            processSuccessfulPayment(payment);
        }, () -> log.error("No payment record found for reference [{}] in webhook", reference));
    }

    private void handleChargeFailed(JsonNode data) {
        String reference = data.path("reference").asText();
        log.warn("Processing charge.failed for reference [{}]", reference);

        paymentRepo.findByPaystackReference(reference).ifPresentOrElse(payment -> {
            if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
                log.info("Payment [{}] already processed as FAILED — skipping (idempotent)", reference);
                return;
            }
            processFailedPayment(payment);
        }, () -> log.error("No payment record found for reference [{}] in webhook", reference));
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — PAYMENT OUTCOME PROCESSORS
    // ═══════════════════════════════════════════════════════════

    private void processSuccessfulPayment(Payment payment) {
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepo.save(payment);

        UUID    orderId    = payment.getOrder().getId();
        boolean isPreOrder = isPreOrderOrder(payment.getOrder());

        if (isPreOrder) {
            // Deposit confirmed — PreOrderService takes over from here:
            //   creates PreOrderRecord, updates order to DEPOSIT_PAID,
            //   clears cart, opens order chat, notifies customer.
            log.info("Payment SUCCESS (deposit) for pre-order [{}] — routing to PreOrderService", orderId);
            preOrderService.handleDepositPaid(orderId);
        } else {
            // Full payment confirmed — confirm order and open chat normally.
            log.info("Payment SUCCESS for order [{}] — confirming normally", orderId);
            orderService.confirmOrderAfterPayment(orderId);
            chatService.createOrderChat(orderId);
        }

        log.info("Payment flow complete for order [{}] — preOrder: {}", orderId, isPreOrder);
    }

    private void processFailedPayment(Payment payment) {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        paymentRepo.save(payment);

        UUID orderId = payment.getOrder().getId();
        log.warn("Payment FAILED — updating order [{}] to PAYMENT_FAILED", orderId);
        orderService.markOrderPaymentFailed(orderId);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — PRE-ORDER DETECTION HELPER
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns true if ANY item in the order is a product with
     * StockStatus.PRE_ORDER or StockStatus.COMING_SOON.
     *
     * Extracted as a helper so the same logic isn't duplicated across
     * initializePayment(), processSuccessfulPayment(), and verifyPayment().
     */
    private boolean isPreOrderOrder(Order order) {
        return order.getOrderItems().stream()
                .anyMatch(item ->
                        item.getProduct().getStockStatus() == StockStatus.PRE_ORDER
                                || item.getProduct().getStockStatus() == StockStatus.COMING_SOON);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — SIGNATURE VERIFICATION
    // ═══════════════════════════════════════════════════════════

    private boolean isValidSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(
                    paystackSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexHash = new StringBuilder();
            for (byte b : hash) {
                hexHash.append(String.format("%02x", b));
            }

            boolean valid = hexHash.toString().equals(signature);
            if (!valid) {
                log.warn("Signature mismatch — expected [{}...] got [{}...]",
                        hexHash.toString().substring(0, 16),
                        signature != null
                                ? signature.substring(0, Math.min(16, signature.length()))
                                : "null");
            }
            return valid;
        } catch (Exception e) {
            log.error("Signature verification threw exception: {}", e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — GENERAL HELPERS
    // ═══════════════════════════════════════════════════════════

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(paystackSecretKey);
        return headers;
    }

    private JsonNode parseResponse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("Failed to parse Paystack response: {}", body);
            throw new ApiException("Invalid response from payment gateway");
        }
    }

    private String generateReference(UUID orderId) {
        return "MKT-"
                + orderId.toString().replace("-", "").substring(0, 12).toUpperCase()
                + "-"
                + System.currentTimeMillis();
    }

    private PaymentVerifyResponse buildVerifyResponse(Payment payment, boolean success) {
        return PaymentVerifyResponse.builder()
                .reference(payment.getPaystackReference())
                .orderId(payment.getOrder().getId())
                .paymentStatus(payment.getPaymentStatus().name())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .success(success)
                .build();
    }
}