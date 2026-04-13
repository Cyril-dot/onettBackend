package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.OrderPaymentService;
import com.marketPlace.MarketPlace.dtos.PaymentAdminResponse;
import com.marketPlace.MarketPlace.dtos.PaymentSubmitResponse;
import com.marketPlace.MarketPlace.entity.Enums.PaymentStatus;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/orders")
@RequiredArgsConstructor
public class OrderPaymentController {

    private final OrderPaymentService orderPaymentService;

    // ═══════════════════════════════════════════════════════════
    // CUSTOMER — SUBMIT PAYMENT
    // POST /api/v1/payments/orders/{orderId}/submit
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/{orderId}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PaymentSubmitResponse>> submitPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId,
            @RequestParam("senderAccountName") String senderAccountName,
            @RequestParam("senderPhoneNumber") String senderPhoneNumber,
            @RequestPart("screenshot") MultipartFile screenshot) {

        UUID userId = principal.getUserId();
        log.info("POST /payments/orders/{}/submit — user [{}]", orderId, userId);

        try {
            PaymentSubmitResponse response = orderPaymentService.submitPayment(
                    orderId, userId, senderAccountName, senderPhoneNumber, screenshot);

            log.info("POST /payments/orders/{}/submit — SUCCESS | status [{}]",
                    orderId, response.getPaymentStatus());

            return ResponseEntity.ok(ApiResponse.success(
                    "Payment submitted successfully. Awaiting admin confirmation.", response));

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /payments/orders/{}/submit — NOT FOUND: {}", orderId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            log.warn("POST /payments/orders/{}/submit — BAD REQUEST: {}", orderId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET ALL PAYMENTS
    // GET /api/v1/payments/orders/admin/all
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<PaymentAdminResponse>>> getAllPayments(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("GET /payments/orders/admin/all — seller [{}]", principal.getUserId());

        List<PaymentAdminResponse> payments = orderPaymentService.getAllPayments();

        log.info("GET /payments/orders/admin/all — returned {} record(s)", payments.size());

        return ResponseEntity.ok(ApiResponse.success(
                payments.size() + " payment(s) found", payments));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET PAYMENTS BY STATUS
    // GET /api/v1/payments/orders/admin?status=PENDING
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<PaymentAdminResponse>>> getPaymentsByStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam PaymentStatus status) {

        log.info("GET /payments/orders/admin?status={} — seller [{}]", status, principal.getUserId());

        List<PaymentAdminResponse> payments = orderPaymentService.getPaymentsByStatus(status);

        log.info("GET /payments/orders/admin?status={} — returned {} record(s)",
                status, payments.size());

        return ResponseEntity.ok(ApiResponse.success(
                payments.size() + " payment(s) with status " + status, payments));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET PAYMENT BY ORDER ID
    // GET /api/v1/payments/orders/admin/order/{orderId}
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/admin/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentAdminResponse>> getPaymentByOrderId(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId) {

        log.info("GET /payments/orders/admin/order/{} — seller [{}]", orderId, principal.getUserId());

        try {
            PaymentAdminResponse response = orderPaymentService.getPaymentByOrderId(orderId);
            return ResponseEntity.ok(ApiResponse.success("Payment found", response));

        } catch (ResourceNotFoundException ex) {
            log.warn("GET /payments/orders/admin/order/{} — NOT FOUND: {}", orderId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET PAYMENT BY PAYMENT ID
    // GET /api/v1/payments/orders/admin/{paymentId}
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/admin/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentAdminResponse>> getPaymentById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID paymentId) {

        log.info("GET /payments/orders/admin/{} — seller [{}]", paymentId, principal.getUserId());

        try {
            PaymentAdminResponse response = orderPaymentService.getPaymentById(paymentId);
            return ResponseEntity.ok(ApiResponse.success("Payment found", response));

        } catch (ResourceNotFoundException ex) {
            log.warn("GET /payments/orders/admin/{} — NOT FOUND: {}", paymentId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — CONFIRM PAYMENT
    // POST /api/v1/payments/orders/admin/{orderId}/confirm
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/admin/{orderId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId,
            @RequestParam(required = false) String adminNote) {

        log.info("POST /payments/orders/admin/{}/confirm — seller [{}]", orderId, principal.getUserId());

        try {
            orderPaymentService.confirmPayment(orderId, adminNote);

            log.info("POST /payments/orders/admin/{}/confirm — CONFIRMED", orderId);

            return ResponseEntity.ok(ApiResponse.success("Payment confirmed successfully.", null));

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /payments/orders/admin/{}/confirm — NOT FOUND: {}", orderId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            log.warn("POST /payments/orders/admin/{}/confirm — BAD REQUEST: {}", orderId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — REJECT PAYMENT
    // POST /api/v1/payments/orders/admin/{orderId}/reject
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/admin/{orderId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId,
            @RequestParam(required = false) String adminNote) {

        log.info("POST /payments/orders/admin/{}/reject — seller [{}]", orderId, principal.getUserId());

        try {
            orderPaymentService.rejectPayment(orderId, adminNote);

            log.warn("POST /payments/orders/admin/{}/reject — REJECTED | reason: {}", orderId, adminNote);

            return ResponseEntity.ok(ApiResponse.success("Payment rejected.", null));

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /payments/orders/admin/{}/reject — NOT FOUND: {}", orderId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            log.warn("POST /payments/orders/admin/{}/reject — BAD REQUEST: {}", orderId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
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