package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.NotificationType;
import com.marketPlace.MarketPlace.entity.Enums.OrderStatus;
import com.marketPlace.MarketPlace.entity.Enums.PaymentStatus;
import com.marketPlace.MarketPlace.entity.Enums.StockStatus;
import com.marketPlace.MarketPlace.entity.Repo.*;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepo             orderRepo;
    private final OrderItemRepo         orderItemRepo;
    private final UserRepo              userRepo;
    private final ProductRepo           productRepo;
    private final CartRepo              cartRepo;
    private final PaymentRepo           paymentRepo;
    private final NotificationService   notificationService;

    // ═══════════════════════════════════════════════════════════
    // USER — INITIATE ORDER
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public OrderInitResponse initiateOrder(UUID userId, OrderRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        log.info("Initiating order for user [{}]", user.getEmail());

        List<Cart> cartItems = cartRepo.findByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new ApiException("Cart is empty — cannot place order");
        }

        // PRE-ORDER GUARD: COMING_SOON or PRE_ORDER items must be purchased alone
        boolean isPreOrder = cartItems.stream()
                .anyMatch(c -> c.getProduct().getStockStatus() == StockStatus.COMING_SOON
                        || c.getProduct().getStockStatus() == StockStatus.PRE_ORDER);

        if (isPreOrder && cartItems.size() > 1) {
            throw new ApiException(
                    "COMING_SOON or PRE_ORDER products must be purchased separately — "
                            + "please remove other items from your cart before proceeding.");
        }

        // STOCK VALIDATION (skip for pre-order / coming-soon items)
        for (Cart cart : cartItems) {
            Product product = cart.getProduct();
            boolean isPreOrderType = product.getStockStatus() == StockStatus.COMING_SOON
                    || product.getStockStatus() == StockStatus.PRE_ORDER;

            if (product.getStockStatus() == StockStatus.OUT_OF_STOCK) {
                throw new ApiException("Product is out of stock: " + product.getName());
            }

            if (!isPreOrderType && product.getStock() < cart.getQuantity()) {
                throw new ApiException("Insufficient stock for: " + product.getName()
                        + " (available: " + product.getStock()
                        + ", requested: " + cart.getQuantity() + ")");
            }
        }

        Order order = Order.builder()
                .user(user)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .deliveryAddress(request.getDeliveryAddress())
                .notes(request.getNotes())
                .build();

        Order savedOrder = orderRepo.save(order);

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Cart cart : cartItems) {
            Product product = cart.getProduct();
            BigDecimal unitPrice = product.getDiscounted()
                    ? product.getDiscountPrice() : product.getPrice();
            BigDecimal subTotal = unitPrice.multiply(BigDecimal.valueOf(cart.getQuantity()));

            items.add(OrderItem.builder()
                    .order(savedOrder)
                    .product(product)
                    .quantity(cart.getQuantity())
                    .unitPrice(unitPrice)
                    .subTotal(subTotal)
                    .build());

            total = total.add(subTotal);
        }

        orderItemRepo.saveAll(items);
        savedOrder.setTotal(total);
        savedOrder.setOrderItems(items);
        orderRepo.save(savedOrder);

        BigDecimal chargeAmount = isPreOrder
                ? total.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP)
                : total;

        log.info("Order [{}] initiated for user [{}] — total: {} — chargeAmount: {} — preOrder: {}",
                savedOrder.getId(), user.getEmail(), total, chargeAmount, isPreOrder);

        return OrderInitResponse.builder()
                .orderId(savedOrder.getId())
                .total(total)
                .chargeAmount(chargeAmount)
                .isPreOrder(isPreOrder)
                .customerEmail(user.getEmail())
                .customerName(user.getFullName())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // CALLED BY OrderPaymentService after payment confirmed
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void confirmOrderAfterPayment(UUID orderId) {
        Order order = findOrderById(orderId);

        if (order.getOrderStatus() != OrderStatus.AWAITING_PAYMENT) {
            log.warn("Order [{}] already processed — skipping confirmation (status: {})",
                    orderId, order.getOrderStatus());
            return;
        }

        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            int newStock = product.getStock() - item.getQuantity();
            if (newStock < 0) {
                log.error("Stock went negative for product [{}] after payment — investigate immediately", product.getId());
                newStock = 0;
            }
            product.setStock(newStock);
            productRepo.save(product);
            log.info("Stock deducted for product [{}]: -{} (remaining: {})",
                    product.getName(), item.getQuantity(), newStock);
        }

        orderRepo.updateOrderStatus(orderId, OrderStatus.PENDING, LocalDateTime.now());
        order.setOrderStatus(OrderStatus.PENDING);

        cartRepo.deleteByUserId(order.getUser().getId());

        log.info("Order [{}] confirmed — stock deducted, cart cleared, status: PENDING", orderId);

        notificationService.notifyOrderConfirmed(order);
    }

    // ═══════════════════════════════════════════════════════════
    // CALLED BY OrderPaymentService if payment fails
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void markOrderPaymentFailed(UUID orderId) {
        Order order = findOrderById(orderId);
        orderRepo.updateOrderStatus(orderId, OrderStatus.PAYMENT_FAILED, LocalDateTime.now());
        order.setOrderStatus(OrderStatus.PAYMENT_FAILED);
        log.warn("Order [{}] marked PAYMENT_FAILED", orderId);
        notificationService.notifyPaymentFailed(order);
    }

    // ═══════════════════════════════════════════════════════════
    // USER — CANCEL ORDER (within 2 hours)
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public OrderResponse cancelOrderByUser(UUID orderId, UUID userId) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new ApiException("Order is already cancelled");
        }
        if (order.getOrderStatus() == OrderStatus.DELIVERED) {
            throw new ApiException("Cannot cancel a delivered order");
        }
        if (order.getOrderStatus() == OrderStatus.SHIPPED) {
            throw new ApiException("Cannot cancel a shipped order — contact support");
        }
        if (order.getOrderStatus() == OrderStatus.AWAITING_PAYMENT
                || order.getOrderStatus() == OrderStatus.PAYMENT_FAILED) {
            orderRepo.updateOrderStatus(orderId, OrderStatus.CANCELLED, LocalDateTime.now());
            order.setOrderStatus(OrderStatus.CANCELLED);
            log.info("Order [{}] cancelled by user [{}] — was never paid", orderId, userId);
            return mapToOrderResponse(order);
        }

        long minutesSincePlaced = ChronoUnit.MINUTES.between(order.getCreatedAt(), LocalDateTime.now());
        if (minutesSincePlaced > 120) {
            throw new ApiException("Cancellation window has passed — orders can only be cancelled within 2 hours of placement");
        }

        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
            productRepo.save(product);
            log.info("Stock restored for product [{}]: +{}", product.getName(), item.getQuantity());
        }

        orderRepo.updateOrderStatus(orderId, OrderStatus.CANCELLED, LocalDateTime.now());
        order.setOrderStatus(OrderStatus.CANCELLED);

        log.info("Order [{}] cancelled by user [{}] within 2hr window", orderId, userId);

        notificationService.notifyOrderCancelled(order, "User");
        return mapToOrderResponse(order);
    }

    // ═══════════════════════════════════════════════════════════
    // USER — VIEW ORDERS
    // ═══════════════════════════════════════════════════════════

    public List<OrderResponse> getUserOrders(UUID userId) {
        log.info("Fetching orders for user [{}]", userId);
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderDetails(UUID orderId, UUID userId) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        log.info("Fetching order details [{}] for user [{}]", orderId, userId);
        return mapToOrderResponse(order);
    }

    public List<OrderResponse> getUserOrdersByStatus(UUID userId, OrderStatus status) {
        log.info("Fetching [{}] orders for user [{}]", status, userId);
        return orderRepo.findByUserIdAndOrderStatus(userId, status)
                .stream().map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — STATUS MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = findOrderById(orderId);
        OrderStatus oldStatus = order.getOrderStatus();

        if (oldStatus == OrderStatus.CANCELLED) {
            throw new ApiException("Cannot update a cancelled order");
        }
        if (oldStatus == OrderStatus.DELIVERED && newStatus != OrderStatus.CANCELLED) {
            throw new ApiException("Order already delivered");
        }

        orderRepo.updateOrderStatus(orderId, newStatus, LocalDateTime.now());
        order.setOrderStatus(newStatus);

        log.info("[ADMIN] Order [{}] status: {} -> {}", orderId, oldStatus, newStatus);

        notificationService.notifyOrderStatusChanged(order, oldStatus, newStatus);

        return mapToOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrderByAdmin(UUID orderId) {
        Order order = findOrderById(orderId);

        if (order.getOrderStatus() == OrderStatus.DELIVERED) {
            throw new ApiException("Cannot cancel a delivered order");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new ApiException("Order is already cancelled");
        }

        if (order.getOrderStatus() != OrderStatus.AWAITING_PAYMENT
                && order.getOrderStatus() != OrderStatus.PAYMENT_FAILED) {
            for (OrderItem item : order.getOrderItems()) {
                Product product = item.getProduct();
                product.setStock(product.getStock() + item.getQuantity());
                productRepo.save(product);
                log.info("[ADMIN] Stock restored for product [{}]: +{}", product.getName(), item.getQuantity());
            }
        }

        orderRepo.updateOrderStatus(orderId, OrderStatus.CANCELLED, LocalDateTime.now());
        order.setOrderStatus(OrderStatus.CANCELLED);

        log.warn("[ADMIN] Order [{}] cancelled by admin", orderId);

        notificationService.notifyOrderCancelled(order, "Admin");
        return mapToOrderResponse(order);
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — VIEW ALL ORDERS
    // ═══════════════════════════════════════════════════════════

    public List<OrderResponse> getAllOrders() {
        log.info("[ADMIN] Fetching all orders");
        return orderRepo.findAll().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        log.info("[ADMIN] Fetching orders by status [{}]", status);
        return orderRepo.findByOrderStatus(status)
                .stream().map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersToday() {
        LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        return getOrdersByDateRange(start, start.plusDays(1));
    }

    public List<OrderResponse> getOrdersThisWeek() {
        return getOrdersByDateRange(LocalDateTime.now().minusDays(7), LocalDateTime.now());
    }

    public List<OrderResponse> getOrdersThisMonth() {
        LocalDateTime start = LocalDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        return getOrdersByDateRange(start, LocalDateTime.now());
    }

    public List<OrderResponse> getOrdersByDateRange(LocalDateTime from, LocalDateTime to) {
        log.info("[ADMIN] Fetching orders from {} to {}", from.toLocalDate(), to.toLocalDate());
        return orderRepo.findOrdersByDateRange(from, to)
                .stream().map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — DASHBOARD SUMMARY
    // ═══════════════════════════════════════════════════════════

    public Map<String, Object> getOrderSummary() {
        log.info("[ADMIN] Generating order summary");
        List<Order> all = orderRepo.findAll();

        BigDecimal totalRevenue = all.stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.CANCELLED
                        && o.getOrderStatus() != OrderStatus.AWAITING_PAYMENT
                        && o.getOrderStatus() != OrderStatus.PAYMENT_FAILED)
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal weekRevenue = orderRepo
                .findOrdersByDateRange(LocalDateTime.now().minusDays(7), LocalDateTime.now())
                .stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.CANCELLED
                        && o.getOrderStatus() != OrderStatus.AWAITING_PAYMENT
                        && o.getOrderStatus() != OrderStatus.PAYMENT_FAILED)
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders",       all.size());
        summary.put("awaitingPayment",   countByStatus(all, OrderStatus.AWAITING_PAYMENT));
        summary.put("paymentFailed",     countByStatus(all, OrderStatus.PAYMENT_FAILED));
        summary.put("depositPaid",       countByStatus(all, OrderStatus.DEPOSIT_PAID));
        summary.put("pending",           countByStatus(all, OrderStatus.PENDING));
        summary.put("confirmed",         countByStatus(all, OrderStatus.CONFIRMED));
        summary.put("shipped",           countByStatus(all, OrderStatus.SHIPPED));
        summary.put("delivered",         countByStatus(all, OrderStatus.DELIVERED));
        summary.put("cancelled",         countByStatus(all, OrderStatus.CANCELLED));
        summary.put("totalRevenue",      totalRevenue);
        summary.put("revenueThisWeek",   weekRevenue);

        log.info("[ADMIN] Order summary generated: {} total orders", all.size());
        return summary;
    }

    public Map<String, Long> getOrderCountPerDayLastWeek() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        return orderRepo.findOrdersByDateRange(weekAgo, LocalDateTime.now())
                .stream()
                .collect(Collectors.groupingBy(
                        o -> o.getCreatedAt().toLocalDate().toString(),
                        Collectors.counting()
                ));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER — VIEW THEIR ORDERS
    // ═══════════════════════════════════════════════════════════

    public List<OrderResponse> getSellerOrders(UUID sellerId) {
        log.info("Fetching orders for seller [{}]", sellerId);
        return orderRepo.findOrdersBySellerId(sellerId)
                .stream().map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSellerRevenueSummary(UUID sellerId) {
        log.info("Generating revenue summary for seller [{}]", sellerId);

        List<Order> sellerOrders = orderRepo.findOrdersBySellerId(sellerId);

        List<Order> revenueOrders = sellerOrders.stream()
                .filter(o -> o.getOrderStatus() == OrderStatus.CONFIRMED
                        || o.getOrderStatus() == OrderStatus.SHIPPED
                        || o.getOrderStatus() == OrderStatus.DELIVERED)
                .toList();

        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime todayStart  = now.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime weekStart   = now.minusDays(7);
        LocalDateTime monthStart  = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

        BigDecimal totalRevenue   = sumRevenue(revenueOrders, null,       null);
        BigDecimal dailyRevenue   = sumRevenue(revenueOrders, todayStart, now);
        BigDecimal weeklyRevenue  = sumRevenue(revenueOrders, weekStart,  now);
        BigDecimal monthlyRevenue = sumRevenue(revenueOrders, monthStart, now);

        Map<String, BigDecimal> dailyBreakdown = revenueOrders.stream()
                .filter(o -> o.getCreatedAt().isAfter(weekStart))
                .collect(Collectors.groupingBy(
                        o -> o.getCreatedAt().toLocalDate().toString(),
                        Collectors.reducing(BigDecimal.ZERO, Order::getTotal, BigDecimal::add)
                ));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sellerId",       sellerId);
        summary.put("totalOrders",    sellerOrders.size());
        summary.put("revenueOrders",  revenueOrders.size());
        summary.put("totalRevenue",   totalRevenue);
        summary.put("dailyRevenue",   dailyRevenue);
        summary.put("weeklyRevenue",  weeklyRevenue);
        summary.put("monthlyRevenue", monthlyRevenue);
        summary.put("dailyBreakdown", dailyBreakdown);

        log.info("Revenue summary for seller [{}]: total={} | today={} | week={} | month={}",
                sellerId, totalRevenue, dailyRevenue, weeklyRevenue, monthlyRevenue);

        return summary;
    }

    public List<OrderResponse> getSellerOrdersByStatus(UUID sellerId, OrderStatus status) {
        log.info("Fetching [{}] orders for seller [{}]", status, sellerId);
        return orderRepo.findOrdersBySellerIdAndStatus(sellerId, status)
                .stream().map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(UUID orderId) {
        Order order = findOrderById(orderId);
        log.info("[ADMIN] Fetching order details [{}]", orderId);
        return mapToOrderResponse(order);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    Order findOrderById(UUID orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> {
                    log.error("Order not found: {}", orderId);
                    return new ResourceNotFoundException("Order not found: " + orderId);
                });
    }

    private long countByStatus(List<Order> orders, OrderStatus status) {
        return orders.stream().filter(o -> o.getOrderStatus() == status).count();
    }

    private boolean canUserCancelOrder(Order order) {
        if (order.getOrderStatus() == OrderStatus.SHIPPED
                || order.getOrderStatus() == OrderStatus.DELIVERED
                || order.getOrderStatus() == OrderStatus.CANCELLED) {
            return false;
        }
        long minutesSincePlaced = ChronoUnit.MINUTES.between(order.getCreatedAt(), LocalDateTime.now());
        return minutesSincePlaced <= 120;
    }

    private BigDecimal sumRevenue(List<Order> orders, LocalDateTime from, LocalDateTime to) {
        return orders.stream()
                .filter(o -> from == null || !o.getCreatedAt().isBefore(from))
                .filter(o -> to   == null || !o.getCreatedAt().isAfter(to))
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private OrderItemResponse mapToOrderItemResponse(OrderItem item) {
        String primaryImage = item.getProduct().getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .primaryImageUrl(primaryImage)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subTotal(item.getSubTotal())
                .orderedAt(item.getOrderedAt())
                .build();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        String paymentReference = paymentRepo.findByOrderId(order.getId())
                .map(Payment::getPaystackReference)
                .orElse(null);

        PaymentStatus paymentStatus = paymentRepo.findByOrderId(order.getId())
                .map(Payment::getPaymentStatus)
                .orElse(null);

        return OrderResponse.builder()
                .orderId(order.getId())
                .customerName(order.getUser().getFullName())
                .customerEmail(order.getUser().getEmail())
                .orderStatus(order.getOrderStatus().name())
                .paymentStatus(paymentStatus != null ? paymentStatus.name() : "N/A")
                .paymentReference(paymentReference)
                .total(order.getTotal())
                .deliveryAddress(order.getDeliveryAddress())
                .notes(order.getNotes())
                .canCancel(canUserCancelOrder(order))
                .orderItems(order.getOrderItems().stream()
                        .map(this::mapToOrderItemResponse)
                        .collect(Collectors.toList()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}