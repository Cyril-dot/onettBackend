package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal;
import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.NotificationService;
import com.marketPlace.MarketPlace.dtos.NotificationResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // ═══════════════════════════════════════════════════════════
    // USER ENDPOINTS
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUserNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        UUID userId = principal.getUserId();
        log.info("GET /notifications/user — user [{}] unreadOnly={}", userId, unreadOnly);

        List<NotificationResponse> notifications = unreadOnly
                ? notificationService.getUnreadNotifications(userId)
                : notificationService.getUserNotifications(userId);

        log.info("GET /notifications/user — {} notification(s) returned for user [{}]",
                notifications.size(), userId);

        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved", notifications));
    }

    @GetMapping("/user/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUserUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();
        log.debug("GET /notifications/user/unread-count — user [{}]", userId);

        long count = notificationService.getUnreadCount(userId);
        log.debug("GET /notifications/user/unread-count — {} unread for user [{}]", count, userId);

        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
                Map.of("unreadCount", count)));
    }

    @PatchMapping("/user/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markUserNotificationAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID notificationId) {

        UUID userId = principal.getUserId();
        log.info("PATCH /notifications/user/{}/read — user [{}]", notificationId, userId);

        notificationService.markAsRead(notificationId, userId);

        log.info("PATCH /notifications/user/{}/read — SUCCESS for user [{}]",
                notificationId, userId);

        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    @PatchMapping("/user/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllUserNotificationsAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();
        log.info("PATCH /notifications/user/read-all — user [{}]", userId);

        notificationService.markAllAsRead(userId);

        log.info("PATCH /notifications/user/read-all — SUCCESS for user [{}]", userId);

        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @PostMapping("/user/fcm-token")
    public ResponseEntity<ApiResponse<Void>> registerUserFcmToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, @NotBlank String> payload) {

        UUID userId = principal.getUserId();
        String fcmToken = payload.get("fcmToken");

        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("POST /notifications/user/fcm-token — missing token for user [{}]", userId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Request body must contain a non-blank 'fcmToken'"));
        }

        log.info("POST /notifications/user/fcm-token — registering token for user [{}]", userId);
        notificationService.saveOrUpdateFcmToken(userId, fcmToken);
        log.info("POST /notifications/user/fcm-token — token saved for user [{}]", userId);

        return ResponseEntity.ok(ApiResponse.success("FCM token registered", null));
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER / ADMIN ENDPOINTS
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/seller")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getSellerNotifications(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /notifications/seller — seller [{}] unreadOnly={}", sellerId, unreadOnly);

        List<NotificationResponse> notifications = unreadOnly
                ? notificationService.getUnreadAdminNotifications(sellerId)
                : notificationService.getAdminNotifications(sellerId);

        log.info("GET /notifications/seller — {} notification(s) returned for seller [{}]",
                notifications.size(), sellerId);

        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved", notifications));
    }

    @GetMapping("/seller/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getSellerUnreadCount(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.debug("GET /notifications/seller/unread-count — seller [{}]", sellerId);

        long count = notificationService.getAdminUnreadCount(sellerId);
        log.debug("GET /notifications/seller/unread-count — {} unread for seller [{}]",
                count, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
                Map.of("unreadCount", count)));
    }

    @PatchMapping("/seller/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markSellerNotificationAsRead(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID notificationId) {

        UUID sellerId = principal.getSellerId();
        log.info("PATCH /notifications/seller/{}/read — seller [{}]", notificationId, sellerId);

        notificationService.markAdminNotificationAsRead(notificationId, sellerId);

        log.info("PATCH /notifications/seller/{}/read — SUCCESS for seller [{}]",
                notificationId, sellerId);

        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    @PatchMapping("/seller/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllSellerNotificationsAsRead(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("PATCH /notifications/seller/read-all — seller [{}]", sellerId);

        notificationService.markAllAdminNotificationsAsRead(sellerId);

        log.info("PATCH /notifications/seller/read-all — SUCCESS for seller [{}]", sellerId);

        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @PostMapping("/seller/fcm-token")
    public ResponseEntity<ApiResponse<Void>> registerSellerFcmToken(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestBody Map<String, @NotBlank String> payload) {

        UUID sellerId = principal.getSellerId();
        String fcmToken = payload.get("fcmToken");

        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("POST /notifications/seller/fcm-token — missing token for seller [{}]",
                    sellerId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Request body must contain a non-blank 'fcmToken'"));
        }

        log.info("POST /notifications/seller/fcm-token — registering token for seller [{}]",
                sellerId);
        notificationService.saveOrUpdateSellerFcmToken(sellerId, fcmToken);
        log.info("POST /notifications/seller/fcm-token — token saved for seller [{}]", sellerId);

        return ResponseEntity.ok(ApiResponse.success("FCM token registered", null));
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