package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.Service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // ═══════════════════════════════════════════════════════════
    // ADD TO CART  —  POST /api/v1/cart
    // ═══════════════════════════════════════════════════════════
    @PostMapping
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddToCartRequest request) {

        UUID userId = principal.getUserId();
        log.info("POST /cart — user [{}] adding productId [{}] qty [{}]",
                userId, request.getProductId(), request.getQuantity());

        try {
            CartResponse cart = cartService.addToCart(userId, request);
            log.info("POST /cart — SUCCESS for user [{}]: cart now has {} item(s)", userId, cart.getTotalItems());
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Item added to cart", cart));

        } catch (RuntimeException ex) {
            log.warn("POST /cart — FAILED for user [{}]: {}", userId, ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VIEW CART  —  GET /api/v1/cart
    // ═══════════════════════════════════════════════════════════
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();
        log.info("GET /cart — user [{}]", userId);

        CartResponse cart = cartService.getCart(userId);
        log.info("GET /cart — user [{}]: {} item(s) | total: {}", userId, cart.getTotalItems(), cart.getCartTotal());

        return ResponseEntity.ok(ApiResponse.success("Cart retrieved", cart));
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE QUANTITY  —  PATCH /api/v1/cart/{cartItemId}
    // ═══════════════════════════════════════════════════════════
    @PatchMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateQuantity(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID cartItemId,
            @RequestBody Map<String, Integer> payload) {

        UUID userId = principal.getUserId();
        Integer newQuantity = payload.get("quantity");

        if (newQuantity == null) {
            log.warn("PATCH /cart/{} — missing 'quantity' field in payload (user [{}])", cartItemId, userId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Request body must contain 'quantity'"));
        }

        log.info("PATCH /cart/{} — user [{}] requesting qty → {}", cartItemId, userId, newQuantity);

        try {
            CartResponse cart = cartService.updateQuantity(userId, cartItemId, newQuantity);
            String message = newQuantity <= 0 ? "Item removed from cart" : "Cart item quantity updated";
            log.info("PATCH /cart/{} — SUCCESS: {}", cartItemId, message);
            return ResponseEntity.ok(ApiResponse.success(message, cart));

        } catch (RuntimeException ex) {
            log.warn("PATCH /cart/{} — FAILED for user [{}]: {}", cartItemId, userId, ex.getMessage());

            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;

            return ResponseEntity
                    .status(status)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // REMOVE ITEM  —  DELETE /api/v1/cart/{cartItemId}
    // ═══════════════════════════════════════════════════════════
    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeFromCart(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID cartItemId) {

        UUID userId = principal.getUserId();
        log.info("DELETE /cart/{} — user [{}]", cartItemId, userId);

        try {
            CartResponse cart = cartService.removeFromCart(userId, cartItemId);
            log.info("DELETE /cart/{} — SUCCESS for user [{}]", cartItemId, userId);
            return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));

        } catch (RuntimeException ex) {
            log.warn("DELETE /cart/{} — FAILED for user [{}]: {}", cartItemId, userId, ex.getMessage());

            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.NOT_FOUND;

            return ResponseEntity
                    .status(status)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CLEAR CART  —  DELETE /api/v1/cart
    // ═══════════════════════════════════════════════════════════
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();
        log.info("DELETE /cart (clear) — user [{}]", userId);

        cartService.clearCart(userId);
        log.info("DELETE /cart (clear) — SUCCESS for user [{}]", userId);

        return ResponseEntity.ok(ApiResponse.success("Cart cleared", null));
    }

    // ═══════════════════════════════════════════════════════════
    // CART ITEM COUNT  —  GET /api/v1/cart/count
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getCartItemCount(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();
        log.debug("GET /cart/count — user [{}]", userId);

        long count = cartService.getCartItemCount(userId);
        log.debug("GET /cart/count — user [{}]: {} item(s)", userId, count);

        return ResponseEntity.ok(ApiResponse.success("Cart count retrieved", Map.of("count", count)));
    }



    public record ApiResponse<T>(
            boolean success,
            String  message,
            T       data,
            String  timestamp
    ) {
        static <T> ApiResponse<T> success(String message, T data) {
            return new ApiResponse<>(true, message, data, Instant.now().toString());
        }

        static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null, Instant.now().toString());
        }
    }
}