package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.PaystackService;
import com.marketPlace.MarketPlace.dtos.PaystackInitiatePayload;
import com.marketPlace.MarketPlace.dtos.PaystackVerifyPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/product-listing")
@RequiredArgsConstructor
public class PaystackController {

    private final PaystackService paystackService;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════════════════════
    // INITIATE PAYMENT (USER ONLY)
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaystackInitiatePayload>> initiateListingPayment(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();

        log.info("POST /payments/product-listing/initiate — user [{}]", userId);

        try {
            PaystackInitiatePayload response = paystackService.initiatePayment(userId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Payment initialized — redirect user to authorization URL",
                    response
            ));

        } catch (RuntimeException ex) {
            log.warn("INITIATE FAILED for user [{}]: {}", userId, ex.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VERIFY PAYMENT (PUBLIC)
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/verify/{reference}")
    public ResponseEntity<ApiResponse<PaystackVerifyPayload>> verifyListingPayment(
            @PathVariable String reference) {

        log.info("GET /payments/product-listing/verify/{}", reference);

        try {
            PaystackVerifyPayload response = paystackService.verifyPayment(reference);

            boolean paid = Boolean.TRUE.equals(response.getPaid());

            String message;
            if (paid) {
                message = "Payment verified — product listing request approved";
            } else if ("pending".equalsIgnoreCase(response.getStatus())) {
                message = "Payment still processing — please try again shortly";
            } else {
                message = "Payment verification failed";
            }

            return ResponseEntity.ok(ApiResponse.success(message, response));

        } catch (RuntimeException ex) {
            log.warn("VERIFY FAILED for ref [{}]: {}", reference, ex.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // WEBHOOK (PUBLIC - PAYSTACK)
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("x-paystack-signature") String paystackSignature) {

        log.info("WEBHOOK RECEIVED — signature present: {}",
                paystackSignature != null && !paystackSignature.isBlank());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedPayload = objectMapper.readValue(payload, Map.class);

            String eventType = (String) parsedPayload.get("event");
            log.info("WEBHOOK EVENT: [{}]", eventType);

            paystackService.handleWebhook(parsedPayload, payload, paystackSignature);

            return ResponseEntity.ok(ApiResponse.success("Webhook processed", null));

        } catch (RuntimeException ex) {
            log.error("WEBHOOK REJECTED: {}", ex.getMessage());
            return ResponseEntity.ok(ApiResponse.error(ex.getMessage()));

        } catch (Exception ex) {
            log.error("WEBHOOK PARSE ERROR: {}", ex.getMessage());
            return ResponseEntity.ok(ApiResponse.error("Failed to parse webhook payload"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // API RESPONSE WRAPPER
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