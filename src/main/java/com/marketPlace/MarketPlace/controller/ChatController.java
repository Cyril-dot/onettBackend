package com.marketPlace.MarketPlace.controller;

import com.marketPlace.MarketPlace.Config.Security.AdminPrincipal;
import com.marketPlace.MarketPlace.Config.Security.UserPrincipal;
import com.marketPlace.MarketPlace.Service.ChatService;
import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.Enums.SenderType;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationResponse>> startConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StartConversationRequest request) {

        UUID userId = principal.getUserId();

        try {
            ConversationResponse response = chatService.startConversation(userId, request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Conversation started", response));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (ApiException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/conversations/{conversationId}/messages/user")
    public ResponseEntity<ApiResponse<MessageResponse>> userSendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {

        UUID userId = principal.getUserId();

        try {
            MessageResponse message = chatService.userSendMessage(userId, conversationId, request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Message sent", message));

        } catch (ApiException ex) {
            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/conversations/{conversationId}/messages/seller")
    public ResponseEntity<ApiResponse<MessageResponse>> sellerSendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {

        UUID sellerId = principal.getUserId();

        try {
            MessageResponse message = chatService.sellerSendMessage(sellerId, conversationId, request);
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

    @PostMapping("/conversations/{conversationId}/delivery")
    public ResponseEntity<ApiResponse<MessageResponse>> submitDeliveryDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody DeliveryDetailsRequest request) {

        UUID userId = principal.getUserId();

        try {
            MessageResponse message = chatService.submitDeliveryDetails(userId, conversationId, request);
            return ResponseEntity.ok(ApiResponse.success("Delivery details submitted", message));

        } catch (ApiException ex) {
            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/conversations/{conversationId}/history")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getChatHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId) {

        UUID viewerId = principal.getUserId();

        try {
            ChatHistoryResponse history = chatService.getChatHistory(conversationId, viewerId);
            return ResponseEntity.ok(ApiResponse.success("Chat history retrieved", history));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/user/conversations")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getUserConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        UUID userId = principal.getUserId();

        List<ConversationResponse> conversations = unreadOnly
                ? chatService.getUserConversationsWithUnread(userId)
                : chatService.getUserConversations(userId);

        return ResponseEntity.ok(ApiResponse.success("Conversations retrieved", conversations));
    }

    @GetMapping("/seller/conversations")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getSellerConversations(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        UUID sellerId = principal.getSellerId();

        List<ConversationResponse> conversations = unreadOnly
                ? chatService.getSellerConversationsWithUnread(sellerId)
                : chatService.getSellerConversations(sellerId);

        return ResponseEntity.ok(ApiResponse.success("Conversations retrieved", conversations));
    }

    @GetMapping("/seller/inbox")
    public ResponseEntity<ApiResponse<List<SellerInboxResponse>>> getSellerInbox(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();

        List<SellerInboxResponse> inbox = chatService.getSellerInbox(sellerId);

        return ResponseEntity.ok(ApiResponse.success("Inbox retrieved", inbox));
    }

    @GetMapping("/user/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCountForUser(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = principal.getUserId();

        long count = chatService.getUnreadCountForUser(userId);

        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
                Map.of("unreadCount", count)));
    }

    @GetMapping("/seller/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCountForSeller(
            @AuthenticationPrincipal AdminPrincipal principal) {

        UUID sellerId = principal.getSellerId();

        long count = chatService.getUnreadCountForSeller(sellerId);

        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
                Map.of("unreadCount", count)));
    }

    @PatchMapping("/conversations/{conversationId}/read")
    public ResponseEntity<ApiResponse<Void>> markConversationAsRead(
            @PathVariable UUID conversationId,
            @RequestParam SenderType senderType) {

        try {
            chatService.markConversationAsRead(conversationId, senderType);
            return ResponseEntity.ok(ApiResponse.success("Conversation marked as read", null));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId) {

        UUID userId = principal.getUserId();

        try {
            chatService.deleteConversation(conversationId, userId);
            return ResponseEntity.ok(ApiResponse.success("Conversation deleted", null));

        } catch (ApiException ex) {
            HttpStatus status = ex.getMessage().startsWith("Unauthorized")
                    ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));

        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
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