package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.ProductRequestService;
import com.marketPlace.MarketPlace.dtos.ProductRequestInitiatePayload;
import com.marketPlace.MarketPlace.dtos.ProductRequestPayload;
import com.marketPlace.MarketPlace.dtos.ProductRequestVerifyPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/product-requests")
@RequiredArgsConstructor
public class ProductRequestController {

    private final ProductRequestService productRequestService;

    // ═══════════════════════════════════════════════════════════
    // STEP 1 — INITIATE PAYMENT (SECURED)
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductRequestInitiatePayload>> initiatePayment(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }

        UUID userId = principal.getUserId();

        log.info("POST /product-requests/initiate — user [{}]", userId);

        try {
            ProductRequestInitiatePayload response =
                    productRequestService.initiatePayment(userId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Payment initialized — redirect user to authorization URL",
                    response
            ));

        } catch (RuntimeException ex) {
            log.warn("Initiate failed for user [{}]: {}", userId, ex.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 2 — VERIFY PAYMENT (PUBLIC)
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/verify/{reference}")
    public ResponseEntity<ApiResponse<ProductRequestVerifyPayload>> verifyPayment(
            @PathVariable String reference) {

        log.info("GET /product-requests/verify/{}", reference);

        try {
            ProductRequestVerifyPayload response =
                    productRequestService.verifyPayment(reference);

            boolean paid = Boolean.TRUE.equals(response.getPaid());

            String message;
            if (paid) {
                message = "Payment verified — you can now upload your product";
            } else if ("pending".equalsIgnoreCase(response.getStatus())) {
                message = "Payment still processing — please try again shortly";
            } else {
                message = "Payment verification failed — please try again";
            }

            return ResponseEntity.ok(ApiResponse.success(message, response));

        } catch (RuntimeException ex) {
            log.warn("Verify failed for ref [{}]: {}", reference, ex.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STEP 3 — GET PRODUCT REQUEST (SECURED)
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
                    response
            ));

        } catch (RuntimeException ex) {
            log.warn("Fetch failed | productRequestId={} | user={}: {}",
                    productRequestId, userId, ex.getMessage());

            HttpStatus status;
            if (ex.getMessage().startsWith("Unauthorized")) {
                status = HttpStatus.FORBIDDEN;
            } else if (ex.getMessage().startsWith("Product request not found")) {
                status = HttpStatus.NOT_FOUND;
            } else {
                status = HttpStatus.BAD_REQUEST;
            }

            return ResponseEntity
                    .status(status)
                    .body(ApiResponse.error(ex.getMessage()));
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