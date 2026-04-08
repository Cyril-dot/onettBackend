package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepo            cartRepo;
    private final ProductRepo         productRepo;
    private final UserRepo            userRepo;
    private final NotificationService notificationService;

    // ═══════════════════════════════════════════════════════════
    // ADD TO CART
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public CartResponse addToCart(UUID userId, AddToCartRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Product product = productRepo.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found: " + request.getProductId()));

        if (product.getStock() < request.getQuantity()) {
            log.warn("Insufficient stock for product [{}] — available: {}", product.getName(), product.getStock());
            throw new RuntimeException("Not enough stock. Available: " + product.getStock());
        }

        // Track whether this is a new item or an update (used for notification below)
        final boolean[] isNewItem = {false};

        // If already in cart → increment quantity
        cartRepo.findByUserIdAndProductId(userId, request.getProductId())
                .ifPresentOrElse(existing -> {
                    int updatedQty = existing.getQuantity() + request.getQuantity();

                    if (product.getStock() < updatedQty) {
                        throw new RuntimeException("Not enough stock. Available: " + product.getStock());
                    }

                    cartRepo.updateQuantity(userId, request.getProductId(), updatedQty);
                    log.info("Cart updated: [{}] qty → {}", product.getName(), updatedQty);

                }, () -> {
                    Cart newItem = Cart.builder()
                            .user(user)
                            .product(product)
                            .quantity(request.getQuantity())
                            .build();
                    cartRepo.save(newItem);
                    isNewItem[0] = true;
                    log.info("Added to cart: [{}] qty: {}", product.getName(), request.getQuantity());
                });

        // Notify user only when a brand-new item is added (not on qty bumps — too noisy)
        if (isNewItem[0]) {
            notificationService.notifyItemAddedToCart(user, product.getName(), request.getQuantity());
        }

        return getCart(userId);
    }

    // ═══════════════════════════════════════════════════════════
    // VIEW CART
    // ═══════════════════════════════════════════════════════════

    public CartResponse getCart(UUID userId) {
        List<Cart> items = cartRepo.findByUserId(userId);

        List<CartItemResponse> itemResponses = items.stream()
                .map(this::mapToCartItemResponse)
                .collect(Collectors.toList());

        BigDecimal cartTotal       = cartRepo.calculateCartTotal(userId);
        BigDecimal discountedTotal = cartRepo.calculateDiscountedCartTotal(userId);
        Integer    totalItems      = cartRepo.totalItemsInCart(userId);

        log.info("Cart loaded for user [{}]: {} item(s) | Total: {}", userId, items.size(), cartTotal);

        return CartResponse.builder()
                .items(itemResponses)
                .cartTotal(cartTotal != null ? cartTotal : BigDecimal.ZERO)
                .discountedTotal(discountedTotal != null ? discountedTotal : BigDecimal.ZERO)
                .totalItems(totalItems != null ? totalItems : 0)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE QUANTITY
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public CartResponse updateQuantity(UUID userId, UUID cartItemId, int newQuantity) {
        Cart item = cartRepo.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found: " + cartItemId));

        if (!item.getUser().getId().equals(userId)) {
            log.warn("Unauthorized cart access — user [{}] tried to modify cart item [{}]", userId, cartItemId);
            throw new RuntimeException("Unauthorized: this cart item does not belong to you");
        }

        if (newQuantity <= 0) {
            cartRepo.delete(item);
            log.info("Removed from cart (qty set to 0): [{}]", item.getProduct().getName());
        } else {
            if (item.getProduct().getStock() < newQuantity) {
                throw new RuntimeException("Not enough stock. Available: " + item.getProduct().getStock());
            }
            cartRepo.updateQuantity(userId, item.getProduct().getId(), newQuantity);
            log.info("Cart item updated: [{}] qty → {}", item.getProduct().getName(), newQuantity);
        }

        return getCart(userId);
    }

    // ═══════════════════════════════════════════════════════════
    // REMOVE SINGLE ITEM
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public CartResponse removeFromCart(UUID userId, UUID cartItemId) {
        Cart item = cartRepo.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found: " + cartItemId));

        if (!item.getUser().getId().equals(userId)) {
            log.warn("Unauthorized cart removal — user [{}] tried to remove cart item [{}]", userId, cartItemId);
            throw new RuntimeException("Unauthorized: this cart item does not belong to you");
        }

        cartRepo.delete(item);
        log.info("Removed from cart: [{}]", item.getProduct().getName());
        return getCart(userId);
    }

    // ═══════════════════════════════════════════════════════════
    // CLEAR ENTIRE CART
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void clearCart(UUID userId) {
        cartRepo.deleteByUserId(userId);
        log.info("Cart cleared for user [{}]", userId);
    }

    // ═══════════════════════════════════════════════════════════
    // CART ITEM COUNT (for badge)
    // ═══════════════════════════════════════════════════════════

    public long getCartItemCount(UUID userId) {
        long count = cartRepo.countByUserId(userId);
        log.debug("Cart item count for user [{}]: {}", userId, count);
        return count;
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private CartItemResponse mapToCartItemResponse(Cart cart) {
        Product product = cart.getProduct();

        BigDecimal unitPrice = product.getDiscounted()
                ? product.getDiscountPrice() : product.getPrice();

        BigDecimal subTotal = unitPrice.multiply(BigDecimal.valueOf(cart.getQuantity()));

        String primaryImage = product.getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);

        return CartItemResponse.builder()
                .cartItemId(cart.getId())
                .productId(product.getId())
                .productName(product.getName())
                .brand(product.getBrand())
                .primaryImageUrl(primaryImage)
                .unitPrice(unitPrice)
                .isDiscounted(product.getDiscounted())
                .originalPrice(product.getPrice())
                .quantity(cart.getQuantity())
                .subTotal(subTotal)
                .stock(product.getStock())
                .addedAt(cart.getAddedAt())
                .build();
    }
}