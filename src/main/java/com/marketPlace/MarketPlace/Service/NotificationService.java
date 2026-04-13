package com.marketPlace.MarketPlace.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.marketPlace.MarketPlace.dtos.NotificationResponse;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.NotificationType;
import com.marketPlace.MarketPlace.entity.Enums.OrderStatus;
import com.marketPlace.MarketPlace.entity.Repo.NotificationRepo;
import com.marketPlace.MarketPlace.entity.Repo.SellerNotificationRepo;
import com.marketPlace.MarketPlace.entity.Repo.SellerRepo;
import com.marketPlace.MarketPlace.entity.Repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepo       notificationRepo;
    private final SellerNotificationRepo sellerNotificationRepo;
    private final UserRepo               userRepo;
    private final SellerRepo             sellerRepo;
    private final JavaMailSender         mailSender;
    private final FirebaseMessaging      firebaseMessaging;

    // ═══════════════════════════════════════════════════════════
    // ORDER CONFIRMED (after payment)
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyOrderConfirmed(Order order) {
        User   user    = order.getUser();
        String title   = "Order Confirmed! 🎉";
        String message = "Your order #" + shortId(order.getId())
                + " has been confirmed. Total: GHS " + order.getTotal()
                + ". Check your chat for delivery coordination.";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.ORDER_CONFIRMED, order.getId().toString());
        sendFirebasePushToUser(user, title, message);
        sendEmail(user.getEmail(), title, buildOrderConfirmedEmailBody(order));
    }

    // ═══════════════════════════════════════════════════════════
    // PAYMENT SUBMITTED — notify seller that a user has paid
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifySellerPaymentReceived(Order order) {
        order.getOrderItems().stream()
                .map(item -> item.getProduct().getSeller())
                .distinct()
                .forEach(seller -> {
                    String title   = "New Order Payment Received 💰";
                    String message = order.getUser().getFullName()
                            + " has paid for order #" + shortId(order.getId())
                            + ". Total: GHS " + order.getTotal()
                            + ". Please prepare the order for delivery.";

                    sendFirebasePushToSeller(seller, title, message);
                    sendEmail(seller.getEmail(), title,
                            buildSellerPaymentReceivedEmailBody(order, seller));

                    log.info("Payment received notification sent to seller [{}] for order [{}]",
                            seller.getEmail(), order.getId());
                });
    }

    // ═══════════════════════════════════════════════════════════
    // ORDER PLACED — notify admin
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyAdminSellerOrderPlaced(Order order) {
        String title   = "New Order Placed — Action Required";
        String message = order.getUser().getFullName()
                + " placed a new order #" + shortId(order.getId())
                + " worth GHS " + order.getTotal()
                + ". Please confirm payment once received.";

        notifyAllAdmins(title, message, NotificationType.ORDER_UPDATE, order.getId().toString());
        notifyAllAdminsByEmail(title, buildAdminNewOrderEmailBody(order));

        log.info("Admin notified of new order [{}] placed by [{}]",
                order.getId(), order.getUser().getEmail());
    }

    // ═══════════════════════════════════════════════════════════
    // PAYMENT FAILED
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyPaymentFailed(Order order) {
        User   user    = order.getUser();
        String title   = "Payment Failed";
        String message = "Your payment for order #" + shortId(order.getId())
                + " could not be processed. Please try again.";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.PAYMENT_FAILED, order.getId().toString());
        sendFirebasePushToUser(user, title, message);
        sendEmail(user.getEmail(), title, buildPaymentFailedEmailBody(order));
    }

    // ═══════════════════════════════════════════════════════════
    // ORDER STATUS CHANGED
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyOrderStatusChanged(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        User   user    = order.getUser();
        String title   = "Order Update — " + formatStatus(newStatus);
        String message = "Your order #" + shortId(order.getId())
                + " status changed from " + formatStatus(oldStatus)
                + " to " + formatStatus(newStatus) + ".";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.ORDER_STATUS_CHANGED, order.getId().toString());
        sendFirebasePushToUser(user, title, message);
        sendEmail(user.getEmail(), title,
                buildStatusChangeEmailBody(order, oldStatus, newStatus));
    }

    // ═══════════════════════════════════════════════════════════
    // ORDER CANCELLED
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyOrderCancelled(Order order, String cancelledBy) {
        User   user    = order.getUser();
        String title   = "Order Cancelled";
        String message = "Your order #" + shortId(order.getId())
                + " has been cancelled"
                + ("Admin".equals(cancelledBy) ? " by our team." : ".")
                + " If you have questions, please contact support.";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.ORDER_CANCELLED, order.getId().toString());
        sendFirebasePushToUser(user, title, message);
        sendEmail(user.getEmail(), title, buildCancelledEmailBody(order, cancelledBy));
    }

    // ═══════════════════════════════════════════════════════════
    // CHAT MESSAGE — USER recipient
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyNewChatMessage(User recipient, String senderName,
                                     UUID conversationId, String messagePreview) {
        String title   = "New message from " + senderName;
        String preview = messagePreview.length() > 80
                ? messagePreview.substring(0, 80) + "..." : messagePreview;

        saveInAppNotification(recipient.getId(), title, preview,
                NotificationType.NEW_MESSAGE, conversationId.toString());
        sendFirebasePushToUser(recipient, title, preview);
    }

    // ═══════════════════════════════════════════════════════════
    // CHAT MESSAGE — SELLER recipient
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyNewChatMessageToSeller(Seller seller, String senderName,
                                             UUID conversationId, String messagePreview) {
        String preview = messagePreview.length() > 80
                ? messagePreview.substring(0, 80) + "..." : messagePreview;
        String title   = "New message from " + senderName;

        sendFirebasePushToSeller(seller, title, preview);
        sendEmail(seller.getEmail(), "New message from " + senderName + " — MarketPlace",
                buildSellerChatEmailBody(seller, senderName, conversationId, preview));

        log.info("Chat notification (push + email) sent to seller [{}] — conversation [{}]",
                seller.getEmail(), conversationId);
    }

    // ═══════════════════════════════════════════════════════════
    // CART UPDATED
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyItemAddedToCart(User user, String productName, int quantity) {
        String title   = "Added to Cart";
        String message = quantity + "x " + productName
                + " has been added to your cart. Ready to checkout?";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.CART_UPDATED, null);
        sendFirebasePushToUser(user, title, message);
    }

    // ═══════════════════════════════════════════════════════════
    // PRODUCT REQUEST NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyProductRequestSubmitted(User user, UUID productRequestId) {
        String title   = "Product Request Received";
        String message = "Your product listing request has been submitted successfully "
                + "and is under review. We'll notify you once it's approved.";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.PRODUCT_REQUEST_SUBMITTED, productRequestId.toString());
        sendFirebasePushToUser(user, title, message);
        sendEmail(user.getEmail(), title,
                buildProductRequestSubmittedEmailBody(user, productRequestId));
    }

    @Async
    public void notifyProductRequestViewed(User user, UUID productRequestId) {
        String title   = "Request Under Review";
        String message = "Your product listing request is currently being reviewed "
                + "by our team. We'll update you shortly.";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.PRODUCT_REQUEST_VIEWED, productRequestId.toString());
        sendFirebasePushToUser(user, title, message);
    }

    @Async
    public void notifyProductRequestApproved(User user, UUID productRequestId) {
        String title   = "Request Approved!";
        String message = "Your product listing request has been approved. "
                + "You can now upload your product to the marketplace.";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.PRODUCT_REQUEST_APPROVED, productRequestId.toString());
        sendFirebasePushToUser(user, title, message);
        sendEmail(user.getEmail(), title,
                buildProductRequestApprovedEmailBody(user, productRequestId));
    }

    @Async
    public void notifyProductRequestRejected(User user, UUID productRequestId, String reason) {
        String title   = "Request Not Approved";
        String message = "Your product listing request was not approved."
                + (reason != null ? " Reason: " + reason : " Please contact support for more details.");

        saveInAppNotification(user.getId(), title, message,
                NotificationType.PRODUCT_REQUEST_REJECTED, productRequestId.toString());
        sendFirebasePushToUser(user, title, message);
        sendEmail(user.getEmail(), title,
                buildProductRequestRejectedEmailBody(user, productRequestId, reason));
    }

    // ═══════════════════════════════════════════════════════════
    // NEW PRODUCT ADDED
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyNewProductAdded(User seller, String productName,
                                      String category, UUID productId) {
        String title   = "New Product Available!";
        String message = productName + " is now available in " + category
                + ". Check it out on MarketPlace!";

        saveInAppNotification(seller.getId(), title, message,
                NotificationType.NEW_PRODUCT_ADDED, productId.toString());
        sendFirebasePushToUser(seller, title, message);
        sendFirebaseTopicPush("all_users", title, message);
    }

    // ═══════════════════════════════════════════════════════════
    // PRE-ORDER NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════

    public void notifyDepositConfirmed(Order order, BigDecimal deposit, BigDecimal remaining) {
        String title   = "Deposit Confirmed";
        String message = String.format(
                "Your deposit of GHS %.2f has been received for your pre-order. "
                        + "We'll notify you as soon as your product is available. "
                        + "Remaining balance: GHS %.2f", deposit, remaining);

        saveInAppNotification(order.getUser().getId(), title, message,
                NotificationType.ORDER_UPDATE, order.getId().toString());
        sendEmailNotification(order.getUser().getEmail(), title, message);

        log.info("Deposit confirmed notification sent to [{}] for order [{}]",
                order.getUser().getEmail(), order.getId());
    }

    public void notifyPreOrderProductAvailable(PreOrderRecord record) {
        String title   = "Your Pre-Order is Available!";
        String message = String.format(
                "%s is now available! Log in and click 'Request Delivery' to complete your purchase. "
                        + "Remaining balance due: GHS %.2f",
                record.getProduct().getName(), record.getRemainingAmount());

        saveInAppNotification(record.getUser().getId(), title, message,
                NotificationType.ORDER_UPDATE, record.getId().toString());
        sendEmailNotification(record.getUser().getEmail(), title, message);

        log.info("Product available notification sent to [{}] for preOrder [{}]",
                record.getUser().getEmail(), record.getId());
    }

    public void notifyAdminDeliveryRequested(PreOrderRecord record) {
        String title   = "Pre-Order Delivery Requested";
        String message = String.format(
                "%s has requested delivery for their pre-order of %s. "
                        + "Remaining amount to collect: GHS %.2f. "
                        + "Please confirm once payment is received.",
                record.getUser().getFullName(),
                record.getProduct().getName(),
                record.getRemainingAmount());

        notifyAllAdmins(title, message, NotificationType.ORDER_UPDATE, record.getId().toString());

        log.info("Delivery request notification sent to admins for preOrder [{}]", record.getId());
    }

    public void notifyPreOrderFullyConfirmed(PreOrderRecord record) {
        String title   = "Order Fully Confirmed";
        String message = String.format(
                "Your full payment for %s has been confirmed. "
                        + "Your order is now confirmed and will be processed for delivery soon!",
                record.getProduct().getName());

        saveInAppNotification(record.getUser().getId(), title, message,
                NotificationType.ORDER_CONFIRMED, record.getOrder().getId().toString());
        sendEmailNotification(record.getUser().getEmail(), title, message);

        log.info("Full confirmation notification sent to [{}] for preOrder [{}]",
                record.getUser().getEmail(), record.getId());
    }

    // ═══════════════════════════════════════════════════════════
    // PRODUCT REQUEST — ADMIN NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════

    @Async
    public void notifyAdminNewListingPaymentSubmitted(User user, UUID productRequestId,
                                                      String senderName, String senderPhone) {
        String title   = "New Listing Payment Submitted";
        String message = user.getFullName() + " submitted a MoMo payment for a product listing. "
                + "Sender: " + senderName + " (" + senderPhone + "). "
                + "Request ID: #" + productRequestId.toString().substring(0, 8).toUpperCase()
                + ". Please review and confirm.";

        notifyAllAdmins(title, message, NotificationType.PRODUCT_REQUEST_SUBMITTED,
                productRequestId.toString());
        notifyAllAdminsByEmail(title,
                buildAdminListingPaymentEmailBody(user, productRequestId, senderName, senderPhone));

        log.info("Admin notified of new listing payment from user [{}] — request [{}]",
                user.getEmail(), productRequestId);
    }

    @Async
    public void notifyAdminListingPaymentRejected(User user, UUID productRequestId, String reason) {
        String title   = "Listing Payment Rejected";
        String message = "Payment for request #"
                + productRequestId.toString().substring(0, 8).toUpperCase()
                + " from " + user.getFullName() + " was rejected."
                + (reason != null ? " Reason: " + reason : "");

        notifyAllAdmins(title, message, NotificationType.PRODUCT_REQUEST_REJECTED,
                productRequestId.toString());

        log.info("Admin notified of rejection for request [{}]", productRequestId);
    }

    @Async
    public void notifyAdminPaymentSubmitted(Order order, String senderAccountName,
                                            String senderPhoneNumber, BigDecimal chargeAmount,
                                            boolean isPreOrder) {
        String orderType = isPreOrder ? "Pre-Order" : "Order";
        String title     = "Payment Submitted — Action Required";
        String message   = order.getUser().getFullName()
                + " submitted a MoMo payment for " + orderType
                + " #" + shortId(order.getId())
                + ". Sender: " + senderAccountName
                + " (" + senderPhoneNumber + ")"
                + ". Amount: GHS " + chargeAmount
                + ". Please verify and confirm.";

        notifyAllAdmins(title, message, NotificationType.ORDER_UPDATE, order.getId().toString());
        notifyAllAdminsByEmail(title, buildAdminPaymentSubmittedEmailBody(
                order, senderAccountName, senderPhoneNumber, chargeAmount, isPreOrder));

        log.info("Admin notified of payment submission for {} [{}] by user [{}]",
                orderType, order.getId(), order.getUser().getEmail());
    }

    @Async
    public void notifyUserPaymentRejected(Order order, String adminNote) {
        User   user    = order.getUser();
        String title   = "Payment Not Confirmed";
        String message = "Your payment for order #" + shortId(order.getId())
                + " could not be verified."
                + (adminNote != null ? " Reason: " + adminNote : " Please contact support.")
                + " Please resubmit your payment proof or try again.";

        saveInAppNotification(user.getId(), title, message,
                NotificationType.PAYMENT_FAILED, order.getId().toString());
        sendFirebasePushToUser(user, title, message);
        sendEmail(user.getEmail(), title, buildUserPaymentRejectedEmailBody(order, adminNote));

        log.info("Payment rejected notification sent to user [{}] for order [{}]",
                user.getEmail(), order.getId());
    }

    // ═══════════════════════════════════════════════════════════
    // GET USER NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════

    public List<NotificationResponse> getUserNotifications(UUID userId) {
        log.info("Fetching notifications for user [{}]", userId);
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getUnreadNotifications(UUID userId) {
        return notificationRepo.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Async
    public void notifyAllUsersNewProduct(String productName, String category, UUID productId) {
        String title   = "New Product Available! 🛍️";
        String message = productName + " is now available in " + category + ". Check it out!";

        List<User> usersWithToken = userRepo.findAllUsersWithValidFcmToken();
        for (User user : usersWithToken) {
            sendFirebasePushToUser(user, title, message);
        }

        List<User> allUsers = userRepo.findAll();
        for (User user : allUsers) {
            saveInAppNotification(user.getId(), title, message,
                    NotificationType.NEW_PRODUCT_ADDED, productId.toString());
        }

        log.info("New product notification sent to {} users", usersWithToken.size());
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepo.countByUserIdAndReadFalse(userId);
    }

    // ═══════════════════════════════════════════════════════════
    // MARK AS READ — USER
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        notificationRepo.findByIdAndUserId(notificationId, userId).ifPresent(n -> {
            n.setRead(true);
            notificationRepo.save(n);
            log.info("Notification [{}] marked as read by user [{}]", notificationId, userId);
        });
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        int count = notificationRepo.markAllAsReadByUserId(userId);
        log.info("Marked {} notifications as read for user [{}]", count, userId);
    }

    // ═══════════════════════════════════════════════════════════
    // SAVE FCM TOKEN
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void saveOrUpdateFcmToken(UUID userId, String fcmToken) {
        userRepo.findById(userId).ifPresent(user -> {
            user.setFcmToken(fcmToken);
            userRepo.save(user);
            log.info("FCM token updated for user [{}]", userId);
        });
    }

    @Transactional
    public void saveOrUpdateSellerFcmToken(UUID sellerId, String fcmToken) {
        sellerRepo.findById(sellerId).ifPresent(seller -> {
            seller.setFcmToken(fcmToken);
            sellerRepo.save(seller);
            log.info("FCM token updated for seller [{}]", sellerId);
        });
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER / ADMIN NOTIFICATION READ & WRITE
    // ═══════════════════════════════════════════════════════════

    public List<NotificationResponse> getAdminNotifications(UUID sellerId) {
        return sellerNotificationRepo
                .findBySeller_IdOrderByCreatedAtDesc(sellerId)
                .stream()
                .map(this::mapSellerNotifToResponse)
                .toList();
    }

    public List<NotificationResponse> getUnreadAdminNotifications(UUID sellerId) {
        return sellerNotificationRepo
                .findBySeller_IdAndReadFalseOrderByCreatedAtDesc(sellerId)
                .stream()
                .map(this::mapSellerNotifToResponse)
                .toList();
    }

    public long getAdminUnreadCount(UUID sellerId) {
        return sellerNotificationRepo.countBySeller_IdAndReadFalse(sellerId);
    }

    @Transactional
    public void markAdminNotificationAsRead(UUID notificationId, UUID sellerId) {
        sellerNotificationRepo.findByIdAndSeller_Id(notificationId, sellerId)
                .ifPresent(n -> {
                    n.setRead(true);
                    sellerNotificationRepo.save(n);
                    log.info("Seller notification [{}] marked as read by seller [{}]",
                            notificationId, sellerId);
                });
    }

    @Transactional
    public void markAllAdminNotificationsAsRead(UUID sellerId) {
        int count = sellerNotificationRepo.markAllAsReadBySellerId(sellerId);
        log.info("Marked {} seller notifications as read for seller [{}]", count, sellerId);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — HELPERS
    // ═══════════════════════════════════════════════════════════

    /** Notify all admins (sellers) with an in-app notification + Firebase push. */
    private void notifyAllAdmins(String title, String message,
                                 NotificationType type, String referenceId) {
        List<Seller> admins = sellerRepo.findAll();
        if (admins.isEmpty()) {
            log.warn("notifyAllAdmins: no admins found — skipping");
            return;
        }
        for (Seller admin : admins) {
            saveSellerInAppNotification(admin, title, message, type, referenceId);
            sendFirebasePushToSeller(admin, title, message);
        }
        log.info("Admin notification sent to {} admin(s): [{}]", admins.size(), title);
    }

    private void notifyAllAdminsByEmail(String subject, String htmlBody) {
        List<Seller> admins = sellerRepo.findAll();
        if (admins.isEmpty()) {
            log.warn("notifyAllAdminsByEmail: no admins found — email not sent");
            return;
        }
        for (Seller admin : admins) {
            sendEmail(admin.getEmail(), subject, htmlBody);
        }
        log.info("Admin email sent to {} admin(s): subject=[{}]", admins.size(), subject);
    }

    private void saveSellerInAppNotification(Seller seller, String title, String message,
                                             NotificationType type, String referenceId) {
        SellerNotification notification = SellerNotification.builder()
                .seller(seller)
                .title(title)
                .message(message)
                .type(type)
                .referenceId(referenceId)
                .read(false)
                .build();
        sellerNotificationRepo.save(notification);
        log.info("Seller in-app notification saved for seller [{}]: type=[{}]",
                seller.getId(), type);
    }

    private void sendEmailNotification(String toEmail, String subject, String plainText) {
        String html = """
                <html><body style="font-family:Arial,sans-serif;color:#333;">
                <p>%s</p>
                <p style="color:#888;font-size:12px;">MarketPlace</p>
                </body></html>
                """.formatted(plainText);
        sendEmail(toEmail, subject, html);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — FIREBASE PUSH (USER)
    // ═══════════════════════════════════════════════════════════

    private void sendFirebasePushToUser(User user, String title, String body) {
        if (user.getFcmToken() == null || user.getFcmToken().isBlank()) {
            log.debug("No FCM token for user [{}] — skipping push", user.getEmail());
            return;
        }
        try {
            com.google.firebase.messaging.Message fcmMessage =
                    com.google.firebase.messaging.Message.builder()
                            .setToken(user.getFcmToken())
                            .setNotification(
                                    com.google.firebase.messaging.Notification.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build()
                            )
                            .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                            .build();

            String response = firebaseMessaging.send(fcmMessage);
            log.info("Firebase push sent to user [{}] — messageId: {}",
                    user.getEmail(), response);

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Stale FCM token for user [{}] — clearing token", user.getEmail());
                user.setFcmToken(null);
                userRepo.save(user);
            } else {
                log.error("Firebase push failed for user [{}]: {}",
                        user.getEmail(), e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — FIREBASE PUSH (SELLER)
    // ═══════════════════════════════════════════════════════════

    private void sendFirebasePushToSeller(Seller seller, String title, String body) {
        if (seller.getFcmToken() == null || seller.getFcmToken().isBlank()) {
            log.debug("No FCM token for seller [{}] — skipping push", seller.getEmail());
            return;
        }
        try {
            com.google.firebase.messaging.Message fcmMessage =
                    com.google.firebase.messaging.Message.builder()
                            .setToken(seller.getFcmToken())
                            .setNotification(
                                    com.google.firebase.messaging.Notification.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build()
                            )
                            .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                            .build();

            String response = firebaseMessaging.send(fcmMessage);
            log.info("Firebase push sent to seller [{}] — messageId: {}",
                    seller.getEmail(), response);

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Stale FCM token for seller [{}] — clearing token", seller.getEmail());
                seller.setFcmToken(null);
                sellerRepo.save(seller);
            } else {
                log.error("Firebase push failed for seller [{}]: {}",
                        seller.getEmail(), e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — FIREBASE TOPIC PUSH
    // ═══════════════════════════════════════════════════════════

    private void sendFirebaseTopicPush(String topic, String title, String body) {
        try {
            com.google.firebase.messaging.Message fcmMessage =
                    com.google.firebase.messaging.Message.builder()
                            .setTopic(topic)
                            .setNotification(
                                    com.google.firebase.messaging.Notification.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build()
                            )
                            .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                            .build();

            String response = firebaseMessaging.send(fcmMessage);
            log.info("Firebase topic push sent to [{}] — messageId: {}", topic, response);

        } catch (FirebaseMessagingException e) {
            log.error("Firebase topic push failed for topic [{}]: {}", topic, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — EMAIL
    // ═══════════════════════════════════════════════════════════

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom("no-reply@marketplace.com", "MarketPlace");
            mailSender.send(mime);
            log.info("Email sent to [{}]: subject=[{}]", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Email send failed to [{}]: {}", to, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — IN-APP NOTIFICATION PERSISTENCE
    // ═══════════════════════════════════════════════════════════

    private void saveInAppNotification(UUID userId, String title, String message,
                                       NotificationType type, String referenceId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Cannot save notification — user [{}] not found", userId);
            return;
        }
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .referenceId(referenceId)
                .read(false)
                .build();
        notificationRepo.save(notification);
        log.info("In-app notification saved for user [{}]: type=[{}]", userId, type);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — MAPPERS
    // ═══════════════════════════════════════════════════════════

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType().name())
                .referenceId(n.getReferenceId())
                .isRead(n.isRead())
                .createdAt(toLocalDateTime(n.getCreatedAt()))
                .build();
    }

    private NotificationResponse mapSellerNotifToResponse(SellerNotification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType().name())
                .referenceId(n.getReferenceId())
                .isRead(n.isRead())
                .createdAt(toLocalDateTime(n.getCreatedAt()))
                .build();
    }

    /**
     * Safely converts an Instant (or LocalDateTime) to LocalDateTime.
     * Handles both field types in case entities differ.
     */
    private LocalDateTime toLocalDateTime(Object createdAt) {
        if (createdAt == null) return null;
        if (createdAt instanceof LocalDateTime ldt) return ldt;
        if (createdAt instanceof java.time.Instant instant)
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — MISC HELPERS
    // ═══════════════════════════════════════════════════════════

    private String shortId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase();
    }

    private String formatStatus(OrderStatus status) {
        return switch (status) {
            case AWAITING_PAYMENT -> "Awaiting Payment";
            case PAYMENT_FAILED   -> "Payment Failed";
            case DEPOSIT_PAID     -> "Deposit Paid";
            case PENDING          -> "Pending";
            case CONFIRMED        -> "Confirmed";
            case SHIPPED          -> "Shipped";
            case DELIVERED        -> "Delivered";
            case CANCELLED        -> "Cancelled";
        };
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE — EMAIL BODY BUILDERS
    // ═══════════════════════════════════════════════════════════

    private String buildOrderConfirmedEmailBody(Order order) {
        StringBuilder items = new StringBuilder();
        order.getOrderItems().forEach(i ->
                items.append("<tr><td>").append(i.getProduct().getName())
                        .append("</td><td>x").append(i.getQuantity())
                        .append("</td><td>GHS ").append(i.getSubTotal()).append("</td></tr>")
        );
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#2e7d32;">Order Confirmed!</h2>
            <p>Hi %s,</p>
            <p>Your order <strong>#%s</strong> has been confirmed and is being processed.</p>
            <table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;width:100%%;">
              <thead style="background:#f5f5f5;">
                <tr><th>Product</th><th>Qty</th><th>Subtotal</th></tr>
              </thead>
              <tbody>%s</tbody>
            </table>
            <p><strong>Total: GHS %s</strong></p>
            <p>Check your chat on MarketPlace to provide delivery details.</p>
            <p style="color:#888;font-size:12px;">MarketPlace &#8212; Thank you for shopping with us!</p>
            </body></html>
            """.formatted(order.getUser().getFullName(), shortId(order.getId()),
                items.toString(), order.getTotal());
    }

    private String buildSellerPaymentReceivedEmailBody(Order order, Seller seller) {
        StringBuilder items = new StringBuilder();
        order.getOrderItems().stream()
                .filter(i -> i.getProduct().getSeller().getId().equals(seller.getId()))
                .forEach(i ->
                        items.append("<tr><td>").append(i.getProduct().getName())
                                .append("</td><td>x").append(i.getQuantity())
                                .append("</td><td>GHS ").append(i.getSubTotal()).append("</td></tr>")
                );
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#1565c0;">New Order Payment Received 💰</h2>
            <p>Hi %s,</p>
            <p><strong>%s</strong> has paid for order <strong>#%s</strong>.</p>
            <table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;width:100%%;">
              <thead style="background:#f5f5f5;">
                <tr><th>Product</th><th>Qty</th><th>Subtotal</th></tr>
              </thead>
              <tbody>%s</tbody>
            </table>
            <p><strong>Total: GHS %s</strong></p>
            <p>Please prepare the order and coordinate delivery via the chat on your seller dashboard.</p>
            <p style="color:#888;font-size:12px;">MarketPlace</p>
            </body></html>
            """.formatted(seller.getFullName(), order.getUser().getFullName(),
                shortId(order.getId()), items.toString(), order.getTotal());
    }

    private String buildAdminNewOrderEmailBody(Order order) {
        StringBuilder items = new StringBuilder();
        order.getOrderItems().forEach(i ->
                items.append("<tr><td>").append(i.getProduct().getName())
                        .append("</td><td>x").append(i.getQuantity())
                        .append("</td><td>GHS ").append(i.getSubTotal()).append("</td></tr>")
        );
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#e65100;">New Order Placed — Action Required</h2>
            <p>A new order has been placed and is awaiting payment confirmation.</p>
            <p><strong>Customer:</strong> %s (%s)</p>
            <p><strong>Order ID:</strong> #%s</p>
            <table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;width:100%%;">
              <thead style="background:#f5f5f5;">
                <tr><th>Product</th><th>Qty</th><th>Subtotal</th></tr>
              </thead>
              <tbody>%s</tbody>
            </table>
            <p><strong>Total: GHS %s</strong></p>
            <p>Please log in to the admin dashboard to confirm payment once received.</p>
            <p style="color:#888;font-size:12px;">MarketPlace Admin</p>
            </body></html>
            """.formatted(order.getUser().getFullName(), order.getUser().getEmail(),
                shortId(order.getId()), items.toString(), order.getTotal());
    }

    private String buildPaymentFailedEmailBody(Order order) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#c62828;">Payment Failed</h2>
            <p>Hi %s,</p>
            <p>Unfortunately your payment for order <strong>#%s</strong> (GHS %s) could not be processed.</p>
            <p>Please return to MarketPlace and try again. Your cart items are still saved.</p>
            <p style="color:#888;font-size:12px;">MarketPlace Support</p>
            </body></html>
            """.formatted(order.getUser().getFullName(), shortId(order.getId()), order.getTotal());
    }

    private String buildStatusChangeEmailBody(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2>Order Status Update</h2>
            <p>Hi %s,</p>
            <p>Your order <strong>#%s</strong> has been updated:</p>
            <p><strong>%s &#8594; %s</strong></p>
            <p>Total: GHS %s</p>
            <p style="color:#888;font-size:12px;">MarketPlace</p>
            </body></html>
            """.formatted(order.getUser().getFullName(), shortId(order.getId()),
                formatStatus(oldStatus), formatStatus(newStatus), order.getTotal());
    }

    private String buildCancelledEmailBody(Order order, String cancelledBy) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#e65100;">Order Cancelled</h2>
            <p>Hi %s,</p>
            <p>Your order <strong>#%s</strong> has been cancelled%s.</p>
            <p>If you believe this is an error, please contact our support team.</p>
            <p style="color:#888;font-size:12px;">MarketPlace Support</p>
            </body></html>
            """.formatted(order.getUser().getFullName(), shortId(order.getId()),
                "Admin".equals(cancelledBy) ? " by our team" : "");
    }

    private String buildProductRequestSubmittedEmailBody(User user, UUID productRequestId) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#1565c0;">Product Request Received</h2>
            <p>Hi %s,</p>
            <p>We've received your product listing request <strong>#%s</strong>.</p>
            <p>Our team will review your request and get back to you within 24&#8211;48 hours.</p>
            <p>You'll be notified here and via email once a decision has been made.</p>
            <p style="color:#888;font-size:12px;">MarketPlace Team</p>
            </body></html>
            """.formatted(user.getFullName(),
                productRequestId.toString().substring(0, 8).toUpperCase());
    }

    private String buildProductRequestApprovedEmailBody(User user, UUID productRequestId) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#2e7d32;">Your Request Has Been Approved!</h2>
            <p>Hi %s,</p>
            <p>Great news! Your product listing request <strong>#%s</strong> has been approved.</p>
            <p>You can now log in to MarketPlace and upload your product to the marketplace.</p>
            <p>Start selling today and reach thousands of buyers!</p>
            <p style="color:#888;font-size:12px;">MarketPlace Team</p>
            </body></html>
            """.formatted(user.getFullName(),
                productRequestId.toString().substring(0, 8).toUpperCase());
    }

    private String buildProductRequestRejectedEmailBody(User user, UUID productRequestId,
                                                        String reason) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#c62828;">Product Request Not Approved</h2>
            <p>Hi %s,</p>
            <p>Unfortunately your product listing request <strong>#%s</strong> was not approved.</p>
            %s
            <p>If you believe this is an error or need further clarification,
               please contact our support team.</p>
            <p style="color:#888;font-size:12px;">MarketPlace Support</p>
            </body></html>
            """.formatted(user.getFullName(),
                productRequestId.toString().substring(0, 8).toUpperCase(),
                reason != null
                        ? "<p><strong>Reason:</strong> " + reason + "</p>"
                        : "<p>Please contact support for more details.</p>");
    }

    private String buildSellerChatEmailBody(Seller seller, String senderName,
                                            UUID conversationId, String preview) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#1565c0;">New Message from %s</h2>
            <p>Hi %s,</p>
            <p>You have a new message in your MarketPlace inbox:</p>
            <blockquote style="border-left:4px solid #1565c0;padding:8px 16px;color:#555;margin:16px 0;">
              %s
            </blockquote>
            <p>Log in to your seller dashboard to reply and keep the conversation going.</p>
            <p style="color:#888;font-size:12px;">
              Conversation ID: %s &#8212; MarketPlace
            </p>
            </body></html>
            """.formatted(senderName, seller.getFullName(), preview,
                conversationId.toString().substring(0, 8).toUpperCase());
    }

    private String buildAdminListingPaymentEmailBody(User user, UUID productRequestId,
                                                     String senderName, String senderPhone) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#e65100;">New Listing Payment Submitted — Action Required</h2>
            <p>A user has submitted a MoMo payment proof for a product listing fee.</p>
            <table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;width:100%%;">
              <tr><td><strong>User</strong></td><td>%s (%s)</td></tr>
              <tr><td><strong>Request ID</strong></td><td>#%s</td></tr>
              <tr><td><strong>Sender Name</strong></td><td>%s</td></tr>
              <tr><td><strong>Sender Phone</strong></td><td>%s</td></tr>
              <tr><td><strong>Amount</strong></td><td>GHS 100.00</td></tr>
            </table>
            <p>Please log in to the admin dashboard to review the screenshot and confirm or reject.</p>
            <p style="color:#888;font-size:12px;">MarketPlace Admin</p>
            </body></html>
            """.formatted(user.getFullName(), user.getEmail(),
                productRequestId.toString().substring(0, 8).toUpperCase(),
                senderName, senderPhone);
    }

    private String buildAdminPaymentSubmittedEmailBody(Order order, String senderAccountName,
                                                       String senderPhoneNumber,
                                                       BigDecimal chargeAmount,
                                                       boolean isPreOrder) {
        String orderType = isPreOrder ? "Pre-Order" : "Order";
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#e65100;">%s Payment Submitted — Verify &amp; Confirm</h2>
            <p>A customer has submitted a MoMo payment. Please verify the details below and confirm or reject.</p>
            <table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse;width:100%%;">
              <tr><td><strong>Customer</strong></td><td>%s (%s)</td></tr>
              <tr><td><strong>Order ID</strong></td><td>#%s</td></tr>
              <tr><td><strong>Order Type</strong></td><td>%s</td></tr>
              <tr><td><strong>Sender Name</strong></td><td>%s</td></tr>
              <tr><td><strong>Sender Phone</strong></td><td>%s</td></tr>
              <tr><td><strong>Amount</strong></td><td>GHS %s</td></tr>
            </table>
            <p>Please log in to the admin dashboard to review the payment screenshot and take action.</p>
            <p style="color:#888;font-size:12px;">MarketPlace Admin</p>
            </body></html>
            """.formatted(orderType, order.getUser().getFullName(), order.getUser().getEmail(),
                shortId(order.getId()), orderType, senderAccountName,
                senderPhoneNumber, chargeAmount);
    }

    private String buildUserPaymentRejectedEmailBody(Order order, String adminNote) {
        return """
            <html><body style="font-family:Arial,sans-serif;color:#333;">
            <h2 style="color:#c62828;">Payment Could Not Be Verified</h2>
            <p>Hi %s,</p>
            <p>Unfortunately, your MoMo payment for order <strong>#%s</strong>
               (GHS %s) could not be verified by our team.</p>
            %s
            <p>Please log in to MarketPlace and resubmit your payment proof,
               or contact our support team if you believe this is an error.</p>
            <p style="color:#888;font-size:12px;">MarketPlace Support</p>
            </body></html>
            """.formatted(order.getUser().getFullName(), shortId(order.getId()), order.getTotal(),
                adminNote != null
                        ? "<p><strong>Reason:</strong> " + adminNote + "</p>"
                        : "<p>Please contact support for more details.</p>");
    }
}