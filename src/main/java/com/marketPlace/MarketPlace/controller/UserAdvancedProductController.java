package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal;
import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.UserAdvancedProductService;
import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.Enums.ApprovalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/user-products")
@RequiredArgsConstructor
public class UserAdvancedProductController {

    private final UserAdvancedProductService userAdvancedProductService;

    // ═══════════════════════════════════════════════════════════
    // USER — CREATE PRODUCT REQUEST
    // POST /api/v1/user-products/create
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserProductResponse>> createUserProductRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID requestId,
            @RequestPart("product") UserProductRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        UUID userId = principal.getUserId();
        log.info("POST /user-products/create — user [{}] | requestId [{}] | images: {}",
                userId, requestId, images != null ? images.size() : 0);

        try {
            UserProductResponse response = userAdvancedProductService
                    .createUserProductRequest(request, userId, requestId, images);

            log.info("POST /user-products/create — SUCCESS | productId [{}] | user [{}]",
                    response.getId(), userId);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Product request submitted successfully", response));

        } catch (RuntimeException ex) {
            log.warn("POST /user-products/create — FAILED for user [{}]: {}", userId, ex.getMessage());
            HttpStatus status = ex.getMessage().contains("not found") ? HttpStatus.NOT_FOUND
                    : ex.getMessage().contains("Payment not completed") ? HttpStatus.PAYMENT_REQUIRED
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (IOException ex) {
            log.error("POST /user-products/create — image upload failed for user [{}]: {}",
                    userId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Image upload failed — please try again"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // USER — UPDATE PRODUCT (max 3 times)
    // PUT /api/v1/user-products/{productId}
    // ═══════════════════════════════════════════════════════════
    @PutMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserProductResponse>> updateUserProduct(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID productId,
            @RequestPart("product") UpdateUserProductRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> newImages) {

        UUID userId = principal.getUserId();
        log.info("PUT /user-products/{} — user [{}] | newImages: {}",
                productId, userId, newImages != null ? newImages.size() : 0);

        try {
            UserProductResponse response = userAdvancedProductService
                    .updateUserProduct(productId, userId, newImages, request);

            log.info("PUT /user-products/{} — SUCCESS | updateCount now: {} | user [{}]",
                    productId, response.getUpdateCount(), userId);
            return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));

        } catch (RuntimeException ex) {
            log.warn("PUT /user-products/{} — FAILED for user [{}]: {}", productId, userId, ex.getMessage());
            HttpStatus status = ex.getMessage().contains("not found") ? HttpStatus.NOT_FOUND
                    : ex.getMessage().contains("maximum number of updates") ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (IOException ex) {
            log.error("PUT /user-products/{} — image upload failed for user [{}]: {}",
                    productId, userId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Image upload failed — please try again"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // USER — GET MY PRODUCT REQUESTS (paginated)
    // GET /api/v1/user-products/my-requests
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/my-requests")
    public ResponseEntity<ApiResponse<Page<RequestProductResponse>>> getMyProductRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        UUID userId = principal.getUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        log.info("GET /user-products/my-requests — user [{}] page={} size={}", userId, page, size);

        Page<RequestProductResponse> response =
                userAdvancedProductService.getUserProductRequests(userId, pageable);

        log.info("GET /user-products/my-requests — {} record(s) for user [{}]",
                response.getTotalElements(), userId);
        return ResponseEntity.ok(ApiResponse.success("Your product requests retrieved", response));
    }

    // ═══════════════════════════════════════════════════════════
    // USER — GET SINGLE PRODUCT REQUEST BY ID
    // GET /api/v1/user-products/my-requests/{requestId}
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/my-requests/{requestId}")
    public ResponseEntity<ApiResponse<RequestProductResponse>> getMyProductRequestById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId) {

        UUID userId = principal.getUserId();
        log.info("GET /user-products/my-requests/{} — user [{}]", requestId, userId);

        try {
            RequestProductResponse response =
                    userAdvancedProductService.getUserProductRequestById(requestId, userId);
            log.info("GET /user-products/my-requests/{} — SUCCESS for user [{}]", requestId, userId);
            return ResponseEntity.ok(ApiResponse.success("Product request retrieved", response));

        } catch (RuntimeException ex) {
            log.warn("GET /user-products/my-requests/{} — FAILED for user [{}]: {}",
                    requestId, userId, ex.getMessage());
            HttpStatus status = ex.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // USER — GET MY PRODUCTS BY APPROVAL STATUS
    // GET /api/v1/user-products/my-products?status=PENDING&page=0&size=10
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/my-products")
    public ResponseEntity<ApiResponse<Page<UserProductResponse>>> getMyProductsByStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam ApprovalStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        UUID userId = principal.getUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        log.info("GET /user-products/my-products — user [{}] status={} page={} size={}",
                userId, status, page, size);

        Page<UserProductResponse> response =
                userAdvancedProductService.getUserProductsByStatus(userId, status, pageable);

        log.info("GET /user-products/my-products — {} product(s) [{}] for user [{}]",
                response.getTotalElements(), status, userId);
        return ResponseEntity.ok(ApiResponse.success("Your products retrieved", response));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — VIEW ALL PRODUCT REQUESTS
    // GET /api/v1/user-products/seller/all
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/seller/all")
    public ResponseEntity<ApiResponse<List<RequestProductResponse>>> viewAllProductRequests(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /user-products/seller/all — seller [{}]", sellerId);

        try {
            List<RequestProductResponse> response =
                    userAdvancedProductService.viewAllProductRequests(sellerId);
            log.info("GET /user-products/seller/all — {} record(s) for seller [{}]",
                    response.size(), sellerId);
            return ResponseEntity.ok(ApiResponse.success("All product requests retrieved", response));

        } catch (RuntimeException ex) {
            log.warn("GET /user-products/seller/all — FAILED for seller [{}]: {}",
                    sellerId, ex.getMessage());
            HttpStatus status = ex.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — VIEW PRODUCT REQUESTS BY STATUS (paginated)
    // GET /api/v1/user-products/seller/by-status?status=PENDING&page=0&size=10
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/seller/by-status")
    public ResponseEntity<ApiResponse<Page<RequestProductResponse>>> viewProductsByStatus(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam ApprovalStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        UUID sellerId = principal.getSellerId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        log.info("GET /user-products/seller/by-status — seller [{}] status={} page={} size={}",
                sellerId, status, page, size);

        try {
            Page<RequestProductResponse> response =
                    userAdvancedProductService.viewProductsByStatus(sellerId, status, pageable);
            log.info("GET /user-products/seller/by-status — {} record(s) [{}] for seller [{}]",
                    response.getTotalElements(), status, sellerId);
            return ResponseEntity.ok(ApiResponse.success("Product requests retrieved", response));

        } catch (RuntimeException ex) {
            log.warn("GET /user-products/seller/by-status — FAILED for seller [{}]: {}",
                    sellerId, ex.getMessage());
            HttpStatus status2 = ex.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status2).body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — VIEW RECENT PRODUCT REQUESTS (paginated)
    // GET /api/v1/user-products/seller/recent?page=0&size=10
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/seller/recent")
    public ResponseEntity<ApiResponse<Page<RequestProductResponse>>> viewRecentProductRequests(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        log.info("GET /user-products/seller/recent — page={} size={}", page, size);

        Page<RequestProductResponse> response =
                userAdvancedProductService.viewRecentProductRequests(pageable);

        log.info("GET /user-products/seller/recent — {} total request(s)", response.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success("Recent product requests retrieved", response));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — GET SINGLE PRODUCT REQUEST BY ID
    // GET /api/v1/user-products/seller/requests/{requestId}
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/seller/requests/{requestId}")
    public ResponseEntity<ApiResponse<RequestProductResponse>> getProductRequestByIdForSeller(
            @PathVariable UUID requestId) {

        log.info("GET /user-products/seller/requests/{}", requestId);

        try {
            RequestProductResponse response =
                    userAdvancedProductService.getProductRequestByIdForSeller(requestId);
            log.info("GET /user-products/seller/requests/{} — SUCCESS", requestId);
            return ResponseEntity.ok(ApiResponse.success("Product request retrieved", response));

        } catch (RuntimeException ex) {
            log.warn("GET /user-products/seller/requests/{} — FAILED: {}", requestId, ex.getMessage());
            HttpStatus status = ex.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — APPROVE / REJECT PRODUCT REQUEST
    // PATCH /api/v1/user-products/seller/requests/{productId}/status
    // ═══════════════════════════════════════════════════════════
    @PatchMapping("/seller/requests/{productId}/status")
    public ResponseEntity<ApiResponse<AdminUserProductResponse>> adminUpdateProductStatus(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID productId,
            @RequestBody java.util.Map<String, String> payload) {

        UUID sellerId = principal.getSellerId();
        String rawStatus = payload.get("status");

        if (rawStatus == null || rawStatus.isBlank()) {
            log.warn("PATCH /user-products/seller/requests/{}/status — missing 'status' field", productId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Request body must contain 'status'"));
        }

        ApprovalStatus approvalStatus;
        try {
            approvalStatus = ApprovalStatus.valueOf(rawStatus.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("PATCH /user-products/seller/requests/{}/status — invalid status value: {}",
                    productId, rawStatus);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid status value: " + rawStatus
                            + ". Must be one of: PENDING, APPROVED, REJECTED"));
        }

        log.info("PATCH /user-products/seller/requests/{}/status — seller [{}] → {}",
                productId, sellerId, approvalStatus);

        try {
            AdminUserProductResponse response =
                    userAdvancedProductService.adminUserProductUpdate(sellerId, productId, approvalStatus);
            log.info("PATCH /user-products/seller/requests/{}/status — SUCCESS → {}",
                    productId, approvalStatus);
            return ResponseEntity.ok(ApiResponse.success(
                    "Product status updated to " + approvalStatus, response));

        } catch (RuntimeException ex) {
            log.warn("PATCH /user-products/seller/requests/{}/status — FAILED: {}", productId, ex.getMessage());
            HttpStatus status = ex.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
        }
    }

}