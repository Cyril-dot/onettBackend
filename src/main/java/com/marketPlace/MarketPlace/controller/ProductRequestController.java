package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.ProductRequestService;
import com.marketPlace.MarketPlace.dtos.ProductListingPaymentResponse;
import com.marketPlace.MarketPlace.dtos.ProductRequestPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/product-requests")
@RequiredArgsConstructor
public class ProductRequestController {

    private final ProductRequestService productRequestService;

    // ═══════════════════════════════════════════════════════════
    // STEP 1 — USER: Submit MoMo payment proof
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/submit", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductListingPaymentResponse>> submitListingPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("senderAccountName") String senderAccountName,
            @RequestParam("senderPhoneNumber") String senderPhoneNumber,
            @RequestPart("screenshot") MultipartFile screenshot) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }

        UUID userId = principal.getUserId();
        log.info("POST /product-requests/submit — user [{}]", userId);

        try {
            ProductListingPaymentResponse response =
                    productRequestService.submitListingPayment(
                            userId, senderAccountName, senderPhoneNumber, screenshot);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(
                            "Payment proof submitted — pending admin review",
                            response));

        } catch (RuntimeException ex) {
            log.warn("Submit failed for user [{}]: {}", userId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 2 — ADMIN: Confirm MoMo payment
    // ═══════════════════════════════════════════════════════════

    @PatchMapping("/{productRequestId}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> confirmListingPayment(
            @PathVariable UUID productRequestId) {

        log.info("PATCH /product-requests/{}/confirm", productRequestId);

        try {
            productRequestService.confirmListingPayment(productRequestId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Payment confirmed — user has been notified",
                    null));

        } catch (RuntimeException ex) {
            log.warn("Confirm failed for productRequestId [{}]: {}",
                    productRequestId, ex.getMessage());

            HttpStatus status = resolveStatus(ex.getMessage());
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 3 — ADMIN: Reject MoMo payment
    // ═══════════════════════════════════════════════════════════

    @PatchMapping("/{productRequestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectListingPayment(
            @PathVariable UUID productRequestId,
            @RequestParam("reason") String reason) {

        log.info("PATCH /product-requests/{}/reject — reason: {}", productRequestId, reason);

        try {
            productRequestService.rejectListingPayment(productRequestId, reason);

            return ResponseEntity.ok(ApiResponse.success(
                    "Payment rejected — user has been notified",
                    null));

        } catch (RuntimeException ex) {
            log.warn("Reject failed for productRequestId [{}]: {}",
                    productRequestId, ex.getMessage());

            HttpStatus status = resolveStatus(ex.getMessage());
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 4 — USER: Get ProductRequest (after admin confirms)
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/{productRequestId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductRequestPayload>> getProductRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID productRequestId) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }

        UUID userId = principal.getUserId();
        log.info("GET /product-requests/{} — user [{}]", productRequestId, userId);

        try {
            ProductRequestPayload response =
                    productRequestService.getProductRequest(productRequestId, userId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Product request retrieved",
                    response));

        } catch (RuntimeException ex) {
            log.warn("Fetch failed | productRequestId={} | user={}: {}",
                    productRequestId, userId, ex.getMessage());

            HttpStatus status = resolveStatus(ex.getMessage());
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — Resolve HTTP status from exception message
    // ═══════════════════════════════════════════════════════════

    private HttpStatus resolveStatus(String message) {
        if (message == null)                          return HttpStatus.INTERNAL_SERVER_ERROR;
        if (message.startsWith("Unauthorized"))       return HttpStatus.FORBIDDEN;
        if (message.contains("not found"))            return HttpStatus.NOT_FOUND;
        return HttpStatus.BAD_REQUEST;
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