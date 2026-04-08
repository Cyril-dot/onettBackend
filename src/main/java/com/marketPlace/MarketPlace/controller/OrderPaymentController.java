package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.OrderPaymentService;
import com.marketPlace.MarketPlace.dtos.PaymentInitResponse;
import com.marketPlace.MarketPlace.dtos.PaymentVerifyResponse;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/orders")
@RequiredArgsConstructor
public class OrderPaymentController {

    private final OrderPaymentService orderPaymentService;

    // ═══════════════════════════════════════════════════════════
    // INITIALIZE PAYMENT
    // POST /api/v1/payments/orders/{orderId}/initialize
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/{orderId}/initialize")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> initializePayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId) {

        UUID userId = principal.getUserId();

        log.info("POST /payments/orders/{}/initialize — user [{}]", orderId, userId);

        try {
            PaymentInitResponse response =
                    orderPaymentService.initializePayment(orderId, userId);

            log.info("POST /payments/orders/{}/initialize — SUCCESS | ref [{}]",
                    orderId, response.getReference());

            return ResponseEntity.ok(
                    ApiResponse.success(
                            "Payment initialized — redirect user to authorization URL",
                            response
                    )
            );

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /payments/orders/{}/initialize — NOT FOUND: {}", orderId, ex.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            log.warn("POST /payments/orders/{}/initialize — BAD REQUEST: {}", orderId, ex.getMessage());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VERIFY PAYMENT
    // GET /api/v1/payments/orders/verify/{reference}
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/verify/{reference}")
    public ResponseEntity<ApiResponse<PaymentVerifyResponse>> verifyPayment(
            @PathVariable String reference) {

        log.info("GET /payments/orders/verify/{}", reference);

        try {
            PaymentVerifyResponse response =
                    orderPaymentService.verifyPayment(reference);

            log.info("GET /payments/orders/verify/{} — success={} | orderId={}",
                    reference, response.isSuccess(), response.getOrderId());

            String message = response.isSuccess()
                    ? "Payment verified successfully"
                    : "Payment verification failed";

            return ResponseEntity.ok(ApiResponse.success(message, response));

        } catch (ResourceNotFoundException ex) {
            log.warn("GET /payments/orders/verify/{} — NOT FOUND: {}", reference, ex.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            log.warn("GET /payments/orders/verify/{} — BAD REQUEST: {}", reference, ex.getMessage());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PAYSTACK WEBHOOK
    // POST /api/v1/payments/orders/webhook
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("x-paystack-signature") String paystackSignature) {

        log.info("POST /payments/orders/webhook — signature present: {}",
                paystackSignature != null && !paystackSignature.isBlank());

        try {
            orderPaymentService.handleWebhook(payload, paystackSignature);

            log.info("POST /payments/orders/webhook — processed successfully");

            return ResponseEntity.ok(ApiResponse.success("Webhook processed", null));

        } catch (ApiException ex) {
            log.error("POST /payments/orders/webhook — REJECTED: {}", ex.getMessage());

            // Always return 200 to prevent Paystack retries
            return ResponseEntity.ok(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // GENERIC API RESPONSE WRAPPER
    // ═══════════════════════════════════════════════════════════

    public record ApiResponse<T>(
            boolean success,
            String message,
            T data,
            String timestamp
    ) {
        static <T> ApiResponse<T> success(String message, T data) {
            return new ApiResponse<>(true, message, data, Instant.now().toString());
        }

        static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null, Instant.now().toString());
        }
    }
}