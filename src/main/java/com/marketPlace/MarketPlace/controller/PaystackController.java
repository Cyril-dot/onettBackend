package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.PaystackService;
import com.marketPlace.MarketPlace.dtos.ProductListingPaymentResponse;
import com.marketPlace.MarketPlace.dtos.ProductRequestAdminResponse;
import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/product-listing")
@RequiredArgsConstructor
public class PaystackController {

    private final PaystackService paystackService;

    // ═══════════════════════════════════════════════════════════
    // USER — SUBMIT LISTING PAYMENT (MoMo screenshot)
    // POST /api/v1/payments/product-listing/submit
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('USER')")
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductListingPaymentResponse>> submitListingPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("senderAccountName") String senderAccountName,
            @RequestParam("senderPhoneNumber") String senderPhoneNumber,
            @RequestPart("screenshot") MultipartFile screenshot) {

        UUID userId = principal.getUserId();
        log.info("POST /payments/product-listing/submit — user [{}]", userId);

        try {
            ProductListingPaymentResponse response = paystackService.submitListingPayment(
                    userId, senderAccountName, senderPhoneNumber, screenshot);

            log.info("POST /payments/product-listing/submit — SUCCESS | requestId [{}]",
                    response.getProductRequestId());

            return ResponseEntity.ok(ApiResponse.success(
                    "Payment submitted. Awaiting admin confirmation.", response));

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /payments/product-listing/submit — NOT FOUND: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            log.warn("POST /payments/product-listing/submit — BAD REQUEST: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET ALL PAYMENT REQUESTS (paginated)
    // GET /api/v1/payments/product-listing/admin/all
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<Page<ProductRequestAdminResponse>>> getAllPaymentRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.info("GET /payments/product-listing/admin/all — seller [{}]", principal.getUserId());

        Page<ProductRequestAdminResponse> page = paystackService.getAllPaymentRequests(pageable);

        log.info("GET /payments/product-listing/admin/all — returned {} record(s)",
                page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(
                page.getTotalElements() + " request(s) found", page));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET PAYMENT REQUESTS BY STATUS (paginated)
    // GET /api/v1/payments/product-listing/admin?status=PENDING
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<Page<ProductRequestAdminResponse>>> getPaymentRequestsByStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam ApprovalStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.info("GET /payments/product-listing/admin?status={} — seller [{}]",
                status, principal.getUserId());

        Page<ProductRequestAdminResponse> page =
                paystackService.getPaymentRequestsByStatus(status, pageable);

        log.info("GET /payments/product-listing/admin?status={} — returned {} record(s)",
                status, page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(
                page.getTotalElements() + " request(s) with status " + status, page));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET SINGLE PAYMENT REQUEST BY ID
    // GET /api/v1/payments/product-listing/admin/{productRequestId}
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/admin/{productRequestId}")
    public ResponseEntity<ApiResponse<ProductRequestAdminResponse>> getPaymentRequestById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID productRequestId) {

        log.info("GET /payments/product-listing/admin/{} — seller [{}]",
                productRequestId, principal.getUserId());

        try {
            ProductRequestAdminResponse response =
                    paystackService.getPaymentRequestById(productRequestId);

            return ResponseEntity.ok(ApiResponse.success("Payment request found", response));

        } catch (ResourceNotFoundException ex) {
            log.warn("GET /payments/product-listing/admin/{} — NOT FOUND: {}",
                    productRequestId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET DASHBOARD COUNTS BY STATUS
    // GET /api/v1/payments/product-listing/admin/counts
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/admin/counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getPaymentRequestCounts(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("GET /payments/product-listing/admin/counts — seller [{}]", principal.getUserId());

        Map<String, Long> counts = paystackService.getPaymentRequestCounts();

        return ResponseEntity.ok(ApiResponse.success("Payment request counts", counts));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — CONFIRM LISTING PAYMENT
    // POST /api/v1/payments/product-listing/admin/{productRequestId}/confirm
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/admin/{productRequestId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmListingPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID productRequestId) {

        log.info("POST /payments/product-listing/admin/{}/confirm — seller [{}]",
                productRequestId, principal.getUserId());

        try {
            paystackService.confirmListingPayment(productRequestId);

            log.info("POST /payments/product-listing/admin/{}/confirm — CONFIRMED",
                    productRequestId);

            return ResponseEntity.ok(ApiResponse.success(
                    "Listing payment confirmed successfully.", null));

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /payments/product-listing/admin/{}/confirm — NOT FOUND: {}",
                    productRequestId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            log.warn("POST /payments/product-listing/admin/{}/confirm — BAD REQUEST: {}",
                    productRequestId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — REJECT LISTING PAYMENT
    // POST /api/v1/payments/product-listing/admin/{productRequestId}/reject
    // ═══════════════════════════════════════════════════════════

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/admin/{productRequestId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectListingPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID productRequestId,
            @RequestParam(required = false) String reason) {

        log.info("POST /payments/product-listing/admin/{}/reject — seller [{}]",
                productRequestId, principal.getUserId());

        try {
            paystackService.rejectListingPayment(productRequestId, reason);

            log.warn("POST /payments/product-listing/admin/{}/reject — REJECTED | reason: {}",
                    productRequestId, reason);

            return ResponseEntity.ok(ApiResponse.success("Listing payment rejected.", null));

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /payments/product-listing/admin/{}/reject — NOT FOUND: {}",
                    productRequestId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            log.warn("POST /payments/product-listing/admin/{}/reject — BAD REQUEST: {}",
                    productRequestId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
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