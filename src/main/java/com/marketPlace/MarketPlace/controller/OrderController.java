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
import org.springframework.security.core.userdetails.UserDetails;
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

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<OrderInitResponse>> initiateOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrderRequest request) {

        UUID userId = principal.getUserId();
        log.info("POST /orders/initiate — user [{}]", userId);

        try {
            OrderInitResponse response = orderService.initiateOrder(userId, request);
            log.info("POST /orders/initiate — order [{}] created for user [{}] | total: {}",
                    response.getOrderId(), userId, response.getTotal());
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Order initiated — proceed to payment", response));

        } catch (ApiException ex) {
            log.warn("POST /orders/initiate — FAILED for user [{}]: {}", userId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            log.warn("POST /orders/initiate — not found: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) OrderStatus status) {

        UUID userId = principal.getUserId();
        log.info("GET /orders/my-orders — user [{}] status={}", userId, status);

        List<OrderResponse> orders = status != null
                ? orderService.getUserOrdersByStatus(userId, status)
                : orderService.getUserOrders(userId);

        log.info("GET /orders/my-orders — {} order(s) returned for user [{}]", orders.size(), userId);
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
            return ResponseEntity.ok(ApiResponse.success("Order retrieved", order));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PatchMapping("/my-orders/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelMyOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID orderId) {

        UUID userId = principal.getUserId();
        log.info("PATCH /orders/my-orders/{}/cancel — user [{}]", orderId, userId);

        try {
            OrderResponse order = orderService.cancelOrderByUser(orderId, userId);
            return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));

        } catch (ApiException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/seller/orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getSellerOrders(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(required = false) OrderStatus status) {

        UUID sellerId = principal.getSellerId();

        List<OrderResponse> orders = status != null
                ? orderService.getSellerOrdersByStatus(sellerId, status)
                : orderService.getSellerOrders(sellerId);

        return ResponseEntity.ok(ApiResponse.success("Seller orders retrieved", orders));
    }

    @GetMapping("/seller/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSellerRevenue(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();

        Map<String, Object> summary = orderService.getSellerRevenueSummary(sellerId);

        return ResponseEntity.ok(ApiResponse.success("Revenue summary retrieved", summary));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status) {

        List<OrderResponse> orders = status != null
                ? orderService.getOrdersByStatus(status)
                : orderService.getAllOrders();

        return ResponseEntity.ok(ApiResponse.success("All orders retrieved", orders));
    }

    @GetMapping("/admin/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable UUID orderId) {

        try {
            OrderResponse order = orderService.getOrderById(orderId);
            return ResponseEntity.ok(ApiResponse.success("Order retrieved", order));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PatchMapping("/admin/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> payload) {

        String rawStatus = payload.get("status");
        if (rawStatus == null || rawStatus.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Request body must contain 'status'"));
        }

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(rawStatus.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid status value: " + rawStatus));
        }

        try {
            OrderResponse order = orderService.updateOrderStatus(orderId, newStatus);
            return ResponseEntity.ok(ApiResponse.success("Order status updated", order));

        } catch (ApiException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PatchMapping("/admin/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrderByAdmin(
            @PathVariable UUID orderId) {

        try {
            OrderResponse order = orderService.cancelOrderByAdmin(orderId);
            return ResponseEntity.ok(ApiResponse.success("Order cancelled by admin", order));

        } catch (ApiException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/admin/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderSummary() {

        Map<String, Object> summary = orderService.getOrderSummary();
        return ResponseEntity.ok(ApiResponse.success("Order summary retrieved", summary));
    }

    @GetMapping("/admin/date-range")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        if (from.isAfter(to)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("'from' must be before 'to'"));
        }

        List<OrderResponse> orders = orderService.getOrdersByDateRange(from, to);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved", orders));
    }

    @GetMapping("/admin/today")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersToday() {
        List<OrderResponse> orders = orderService.getOrdersToday();
        return ResponseEntity.ok(ApiResponse.success("Today's orders retrieved", orders));
    }

    @GetMapping("/admin/this-week")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersThisWeek() {
        List<OrderResponse> orders = orderService.getOrdersThisWeek();
        return ResponseEntity.ok(ApiResponse.success("This week's orders retrieved", orders));
    }

    @GetMapping("/admin/this-month")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersThisMonth() {
        List<OrderResponse> orders = orderService.getOrdersThisMonth();
        return ResponseEntity.ok(ApiResponse.success("This month's orders retrieved", orders));
    }

    @GetMapping("/admin/daily-counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getOrderCountPerDayLastWeek() {
        Map<String, Long> counts = orderService.getOrderCountPerDayLastWeek();
        return ResponseEntity.ok(ApiResponse.success("Daily order counts retrieved", counts));
    }
    
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