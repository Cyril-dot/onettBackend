package com.marketPlace.MarketPlace.controller;

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

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        UUID userId = principal.getUserId();
        log.info("GET /notifications — user [{}] unreadOnly={}", userId, unreadOnly);

        List<NotificationResponse> notifications = unreadOnly
                ? notificationService.getUnreadNotifications(userId)
                : notificationService.getUserNotifications(userId);

        log.info("GET /notifications — {} notification(s) returned for user [{}]",
                notifications.size(), userId);

        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved", notifications));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();
        log.debug("GET /notifications/unread-count — user [{}]", userId);

        long count = notificationService.getUnreadCount(userId);
        log.debug("GET /notifications/unread-count — {} unread for user [{}]", count, userId);

        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
                Map.of("unreadCount", count)));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID notificationId) {

        UUID userId = principal.getUserId();
        log.info("PATCH /notifications/{}/read — user [{}]", notificationId, userId);

        notificationService.markAsRead(notificationId, userId);

        log.info("PATCH /notifications/{}/read — SUCCESS for user [{}]", notificationId, userId);

        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();
        log.info("PATCH /notifications/read-all — user [{}]", userId);

        notificationService.markAllAsRead(userId);

        log.info("PATCH /notifications/read-all — SUCCESS for user [{}]", userId);

        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> registerFcmToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, @NotBlank String> payload) {

        UUID userId = principal.getUserId();
        String fcmToken = payload.get("fcmToken");

        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("POST /notifications/fcm-token — missing or blank token for user [{}]", userId);
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Request body must contain a non-blank 'fcmToken'"));
        }

        log.info("POST /notifications/fcm-token — registering token for user [{}]", userId);
        notificationService.saveOrUpdateFcmToken(userId, fcmToken);

        log.info("POST /notifications/fcm-token — token saved for user [{}]", userId);

        return ResponseEntity.ok(ApiResponse.success("FCM token registered", null));
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