package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal;
import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.OrderService;
import com.marketPlace.MarketPlace.dtos.OrderInitResponse;
import com.marketPlace.MarketPlace.dtos.OrderRequest;
import com.marketPlace.MarketPlace.dtos.OrderResponse;
import com.marketPlace.MarketPlace.entity.Enums.OrderStatus;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ═══════════════════════════════════════════════════════════
    // USER — INITIATE ORDER
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<OrderInitResponse>> initiateOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrderRequest request) {

        UUID userId = principal.getUserId();
        log.info("POST /orders/initiate — user [{}]", userId);

        try {
            OrderInitResponse response = orderService.initiateOrder(userId, request);
            log.info("POST /orders/initiate — order [{}] created for user [{}] | total: {} | preOrder: {}",
                    response.getOrderId(), userId, response.getTotal(), response.isPreOrder());
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Order initiated — proceed to payment", response));

        } catch (ApiException ex) {
            log.warn("POST /orders/initiate — BAD REQUEST for user [{}]: {}", userId, ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /orders/initiate — NOT FOUND for user [{}]: {}", userId, ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // USER — VIEW ORDERS
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) OrderStatus status) {

        UUID userId = principal.getUserId();
        log.info("GET /orders/my-orders — user [{}] status={}", userId, status);

        List<OrderResponse> orders = status != null
                ? orderService.getUserOrdersByStatus(userId, status)
                : orderService.getUserOrders(userId);

        log.info("GET /orders/my-orders — {} order(s) returned for user [{}]",
                orders.size(), userId);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved", orders));
    }

    @GetMapping("/my-orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId) {

        UUID userId = principal.getUserId();
        log.info("GET /orders/my-orders/{} — user [{}]", orderId, userId);

        try {
            OrderResponse order = orderService.getOrderDetails(orderId, userId);
            log.info("GET /orders/my-orders/{} — SUCCESS for user [{}]", orderId, userId);
            return ResponseEntity.ok(ApiResponse.success("Order retrieved", order));

        } catch (ResourceNotFoundException ex) {
            log.warn("GET /orders/my-orders/{} — NOT FOUND for user [{}]", orderId, userId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // USER — CANCEL ORDER
    // ═══════════════════════════════════════════════════════════

    @PatchMapping("/my-orders/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelMyOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId) {

        UUID userId = principal.getUserId();
        log.info("PATCH /orders/my-orders/{}/cancel — user [{}]", orderId, userId);

        try {
            OrderResponse order = orderService.cancelOrderByUser(orderId, userId);
            log.info("PATCH /orders/my-orders/{}/cancel — SUCCESS for user [{}]", orderId, userId);
            return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));

        } catch (ApiException ex) {
            log.warn("PATCH /orders/my-orders/{}/cancel — BAD REQUEST for user [{}]: {}",
                    orderId, userId, ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            log.warn("PATCH /orders/my-orders/{}/cancel — NOT FOUND for user [{}]",
                    orderId, userId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — VIEW THEIR ORDERS
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/seller/orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getSellerOrders(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(required = false) OrderStatus status) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/seller/orders — seller [{}] status={}", sellerId, status);

        List<OrderResponse> orders = status != null
                ? orderService.getSellerOrdersByStatus(sellerId, status)
                : orderService.getSellerOrders(sellerId);

        log.info("GET /orders/seller/orders — {} order(s) returned for seller [{}]",
                orders.size(), sellerId);
        return ResponseEntity.ok(ApiResponse.success("Seller orders retrieved", orders));
    }

    @GetMapping("/seller/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSellerRevenue(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/seller/revenue — seller [{}]", sellerId);

        Map<String, Object> summary = orderService.getSellerRevenueSummary(sellerId);

        log.info("GET /orders/seller/revenue — SUCCESS for seller [{}]", sellerId);
        return ResponseEntity.ok(ApiResponse.success("Revenue summary retrieved", summary));
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — VIEW ALL ORDERS
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(required = false) OrderStatus status) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/admin/all — admin [{}] status={}", sellerId, status);

        List<OrderResponse> orders = status != null
                ? orderService.getOrdersByStatus(status)
                : orderService.getAllOrders();

        log.info("GET /orders/admin/all — {} order(s) returned for admin [{}]",
                orders.size(), sellerId);
        return ResponseEntity.ok(ApiResponse.success("All orders retrieved", orders));
    }

    @GetMapping("/admin/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID orderId) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/admin/{} — admin [{}]", orderId, sellerId);

        try {
            OrderResponse order = orderService.getOrderById(orderId);
            log.info("GET /orders/admin/{} — SUCCESS for admin [{}]", orderId, sellerId);
            return ResponseEntity.ok(ApiResponse.success("Order retrieved", order));

        } catch (ResourceNotFoundException ex) {
            log.warn("GET /orders/admin/{} — NOT FOUND — admin [{}]", orderId, sellerId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — STATUS MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    @PatchMapping("/admin/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> payload) {

        UUID sellerId = principal.getSellerId();
        log.info("PATCH /orders/admin/{}/status — admin [{}]", orderId, sellerId);

        String rawStatus = payload.get("status");
        if (rawStatus == null || rawStatus.isBlank()) {
            log.warn("PATCH /orders/admin/{}/status — missing 'status' field — admin [{}]",
                    orderId, sellerId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Request body must contain 'status'"));
        }

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(rawStatus.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("PATCH /orders/admin/{}/status — invalid status '{}' — admin [{}]",
                    orderId, rawStatus, sellerId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid status value: " + rawStatus));
        }

        try {
            OrderResponse order = orderService.updateOrderStatus(orderId, newStatus);
            log.info("PATCH /orders/admin/{}/status — updated to [{}] by admin [{}]",
                    orderId, newStatus, sellerId);
            return ResponseEntity.ok(ApiResponse.success("Order status updated", order));

        } catch (ApiException ex) {
            log.warn("PATCH /orders/admin/{}/status — BAD REQUEST: {} — admin [{}]",
                    orderId, ex.getMessage(), sellerId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            log.warn("PATCH /orders/admin/{}/status — NOT FOUND — admin [{}]", orderId, sellerId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PatchMapping("/admin/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrderByAdmin(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID orderId) {

        UUID sellerId = principal.getSellerId();
        log.info("PATCH /orders/admin/{}/cancel — admin [{}]", orderId, sellerId);

        try {
            OrderResponse order = orderService.cancelOrderByAdmin(orderId);
            log.info("PATCH /orders/admin/{}/cancel — SUCCESS by admin [{}]", orderId, sellerId);
            return ResponseEntity.ok(ApiResponse.success("Order cancelled by admin", order));

        } catch (ApiException ex) {
            log.warn("PATCH /orders/admin/{}/cancel — BAD REQUEST: {} — admin [{}]",
                    orderId, ex.getMessage(), sellerId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            log.warn("PATCH /orders/admin/{}/cancel — NOT FOUND — admin [{}]", orderId, sellerId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — DASHBOARD & ANALYTICS
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/admin/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderSummary(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/admin/summary — admin [{}]", sellerId);

        Map<String, Object> summary = orderService.getOrderSummary();

        log.info("GET /orders/admin/summary — SUCCESS for admin [{}]", sellerId);
        return ResponseEntity.ok(ApiResponse.success("Order summary retrieved", summary));
    }

    @GetMapping("/admin/date-range")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByDateRange(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/admin/date-range — admin [{}] from={} to={}", sellerId, from, to);

        if (from.isAfter(to)) {
            log.warn("GET /orders/admin/date-range — invalid range: from={} is after to={} — admin [{}]",
                    from, to, sellerId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("'from' must be before 'to'"));
        }

        List<OrderResponse> orders = orderService.getOrdersByDateRange(from, to);
        log.info("GET /orders/admin/date-range — {} order(s) returned for admin [{}]",
                orders.size(), sellerId);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved", orders));
    }

    @GetMapping("/admin/today")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersToday(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/admin/today — admin [{}]", sellerId);

        List<OrderResponse> orders = orderService.getOrdersToday();
        log.info("GET /orders/admin/today — {} order(s) returned for admin [{}]",
                orders.size(), sellerId);
        return ResponseEntity.ok(ApiResponse.success("Today's orders retrieved", orders));
    }

    @GetMapping("/admin/this-week")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersThisWeek(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/admin/this-week — admin [{}]", sellerId);

        List<OrderResponse> orders = orderService.getOrdersThisWeek();
        log.info("GET /orders/admin/this-week — {} order(s) returned for admin [{}]",
                orders.size(), sellerId);
        return ResponseEntity.ok(ApiResponse.success("This week's orders retrieved", orders));
    }

    @GetMapping("/admin/this-month")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersThisMonth(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/admin/this-month — admin [{}]", sellerId);

        List<OrderResponse> orders = orderService.getOrdersThisMonth();
        log.info("GET /orders/admin/this-month — {} order(s) returned for admin [{}]",
                orders.size(), sellerId);
        return ResponseEntity.ok(ApiResponse.success("This month's orders retrieved", orders));
    }

    @GetMapping("/admin/daily-counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getOrderCountPerDayLastWeek(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /orders/admin/daily-counts — admin [{}]", sellerId);

        Map<String, Long> counts = orderService.getOrderCountPerDayLastWeek();
        log.info("GET /orders/admin/daily-counts — {} day(s) returned for admin [{}]",
                counts.size(), sellerId);
        return ResponseEntity.ok(ApiResponse.success("Daily order counts retrieved", counts));
    }

    // ═══════════════════════════════════════════════════════════
    // SHARED RESPONSE WRAPPER
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