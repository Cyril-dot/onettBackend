package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal;
import com.marketPlace.MarketPlace.Service.AdminChatService;
import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
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

/**
 * AdminChatController
 *
 * All endpoints are secured as AdminPrincipal (seller-role JWT).
 *
 * Base path: /api/v1/admin/chat
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ POST   /orders/{orderId}/start           — seller starts order chat     │
 * │ GET    /inbox                            — rich inbox card list         │
 * │ GET    /conversations                    — flat conversation list       │
 * │ GET    /conversations/{id}/history       — full message thread          │
 * │ POST   /conversations/{id}/reply         — send reply as seller         │
 * │ GET    /unread-count                     — total unread badge count     │
 * │ PATCH  /conversations/{id}/read          — mark conversation as read    │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    private final AdminChatService adminChatService;

    // ═══════════════════════════════════════════════════════════
    // START ORDER CONVERSATION — seller initiates chat from orders page (NEW)
    //
    // POST /api/v1/admin/chat/orders/{orderId}/start
    //
    // Idempotent: if a conversation already exists for this order + seller,
    // it is returned instead of creating a duplicate.
    //
    // Response: ConversationResponse with conversationId so the frontend
    // can immediately navigate to the chat panel.
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/orders/{orderId}/start")
    public ResponseEntity<ApiResponse<ConversationResponse>> startOrderConversation(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID orderId) {

        UUID sellerId = principal.getSellerId();
        log.info("POST /admin/chat/orders/{}/start — seller [{}]", orderId, sellerId);

        try {
            ConversationResponse conversation =
                    adminChatService.startOrderConversation(sellerId, orderId);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Order conversation started", conversation));

        } catch (ApiException ex) {
            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // INBOX — rich card list (product image, last msg, unread count)
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<SellerInboxResponse>>> getInbox(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /admin/chat/inbox — seller [{}]", sellerId);

        List<SellerInboxResponse> inbox = adminChatService.getInbox(sellerId);
        return ResponseEntity.ok(ApiResponse.success("Inbox retrieved", inbox));
    }

    // ═══════════════════════════════════════════════════════════
    // CONVERSATIONS — list view with optional unread filter
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /admin/chat/conversations — seller [{}] unreadOnly={}", sellerId, unreadOnly);

        List<ConversationResponse> convs = adminChatService.getConversations(sellerId, unreadOnly);
        return ResponseEntity.ok(ApiResponse.success("Conversations retrieved", convs));
    }

    // ═══════════════════════════════════════════════════════════
    // CHAT HISTORY — full message thread for a conversation
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/conversations/{conversationId}/history")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getChatHistory(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID conversationId) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /admin/chat/conversations/{}/history — seller [{}]", conversationId, sellerId);

        try {
            ChatHistoryResponse history = adminChatService.getChatHistory(sellerId, conversationId);
            return ResponseEntity.ok(ApiResponse.success("Chat history retrieved", history));

        } catch (ApiException ex) {
            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SEND REPLY — seller posts a message in a conversation
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/conversations/{conversationId}/reply")
    public ResponseEntity<ApiResponse<MessageResponse>> sendReply(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {

        UUID sellerId = principal.getSellerId();
        log.info("POST /admin/chat/conversations/{}/reply — seller [{}]", conversationId, sellerId);

        try {
            MessageResponse message = adminChatService.sendReply(sellerId, conversationId, request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Reply sent", message));

        } catch (ApiException ex) {
            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UNREAD COUNT — for badge in navigation
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();
        log.info("GET /admin/chat/unread-count — seller [{}]", sellerId);

        long count = adminChatService.getUnreadCount(sellerId);
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
                Map.of("unreadCount", count)));
    }

    // ═══════════════════════════════════════════════════════════
    // MARK AS READ — seller opens conversation, clear unread badge
    // ═══════════════════════════════════════════════════════════

    @PatchMapping("/conversations/{conversationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable UUID conversationId) {

        UUID sellerId = principal.getSellerId();
        log.info("PATCH /admin/chat/conversations/{}/read — seller [{}]", conversationId, sellerId);

        try {
            adminChatService.markAsRead(sellerId, conversationId);
            return ResponseEntity.ok(ApiResponse.success("Conversation marked as read", null));

        } catch (ApiException ex) {
            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
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