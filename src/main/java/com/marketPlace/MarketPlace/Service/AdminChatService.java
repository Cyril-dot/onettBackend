package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.SenderType;
import com.marketPlace.MarketPlace.entity.Repo.*;
import com.marketPlace.MarketPlace.exception.ApiException;
import com.marketPlace.MarketPlace.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AdminChatService
 *
 * Provides the seller/admin dashboard with:
 *  - Full inbox listing
 *  - Chat history for any conversation belonging to this seller
 *  - Sending replies as SELLER
 *  - Unread counts and mark-as-read
 *  - Conversation filtering (all / unread-only / by chat type)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private final ConversationsRepo     conversationsRepo;
    private final MessageRepo           messageRepo;
    private final ProductImageRepo      productImageRepo;
    private final SellerRepo            sellerRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService   notificationService;

    // ═══════════════════════════════════════════════════════════
    // GET SELLER INBOX  (rich card view — same as ChatService)
    // ═══════════════════════════════════════════════════════════

    public List<SellerInboxResponse> getInbox(UUID sellerId) {
        log.info("[Admin] Loading inbox for seller [{}]", sellerId);

        return conversationsRepo.findBySellerIdOrderByCreatedAtDesc(sellerId)
                .stream()
                .map(c -> {
                    Message lastMessage = messageRepo.findLatestMessage(c.getId());
                    long    unread      = messageRepo.countByConversationsIdAndIsReadFalse(c.getId());
                    return mapToSellerInboxResponse(c, lastMessage, unread);
                })
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // GET SELLER CONVERSATIONS (list view)
    // ═══════════════════════════════════════════════════════════

    public List<ConversationResponse> getConversations(UUID sellerId, boolean unreadOnly) {
        log.info("[Admin] Fetching conversations for seller [{}] — unreadOnly={}", sellerId, unreadOnly);

        List<Conversations> convs = unreadOnly
                ? conversationsRepo.findSellerConversationsWithUnreadMessages(sellerId)
                : conversationsRepo.findBySellerIdOrderByCreatedAtDesc(sellerId);

        return convs.stream()
                .map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // GET CHAT HISTORY  (seller opens a specific conversation)
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public ChatHistoryResponse getChatHistory(UUID sellerId, UUID conversationId) {
        Conversations conversation = findConversationById(conversationId);

        if (!conversation.getSeller().getId().equals(sellerId)) {
            log.warn("[Admin] Seller [{}] attempted to access conversation [{}] — DENIED",
                    sellerId, conversationId);
            throw new ApiException("Unauthorized: this conversation does not belong to your store");
        }

        // Mark all USER messages as read when seller opens the conversation
        messageRepo.markAsReadBySenderType(conversationId, SenderType.USER);
        log.info("[Admin] Seller [{}] opened conversation [{}] — user messages marked as read",
                sellerId, conversationId);

        List<MessageResponse> messages = messageRepo
                .findByConversationsIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::mapToMessageResponse)
                .collect(Collectors.toList());

        ProductCardPayload productCard = conversation.getProduct() != null
                ? buildProductCardPayload(conversation.getProduct(), conversation, conversation.getUser())
                : null;

        return ChatHistoryResponse.builder()
                .conversationId(conversationId)
                .productCard(productCard)
                .messages(messages)
                .totalMessages(messages.size())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER SENDS REPLY
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public MessageResponse sendReply(UUID sellerId,
                                     UUID conversationId,
                                     SendMessageRequest request) {
        Conversations conversation = findConversationById(conversationId);

        if (!conversation.getSeller().getId().equals(sellerId)) {
            log.warn("[Admin] Unauthorized reply attempt — seller [{}] on conversation [{}]",
                    sellerId, conversationId);
            throw new ApiException("Unauthorized: this conversation does not belong to your store");
        }

        Seller seller = sellerRepo.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found: " + sellerId));

        Message message = Message.builder()
                .conversations(conversation)
                .user(conversation.getUser())
                .senderType(SenderType.SELLER)
                .content(request.getContent())
                .isAutomated(false)
                .repliedAt(LocalDateTime.now())
                .productImage(request.getProductImageId() != null
                        ? resolveProductImage(request.getProductImageId()) : null)
                .build();

        Message saved = messageRepo.save(message);
        MessageResponse response = mapToMessageResponse(saved);

        // ── Push to buyer ────────────────────────────────────────────
        messagingTemplate.convertAndSend(
                "/topic/user/" + conversation.getUser().getId()
                        + "/conversation/" + conversationId,
                response
        );
        messagingTemplate.convertAndSend(
                "/topic/user/" + conversation.getUser().getId() + "/inbox/update",
                buildInboxUpdate(conversation, saved)
        );

        // ── Notify buyer via FCM + in-app ────────────────────────────
        notificationService.notifyNewChatMessage(
                conversation.getUser(),
                seller.getStoreName(),
                conversationId,
                request.getContent()
        );

        log.info("[Admin] Seller [{}] replied in conversation [{}]",
                seller.getStoreName(), conversationId);
        return response;
    }

    // ═══════════════════════════════════════════════════════════
    // UNREAD COUNT
    // ═══════════════════════════════════════════════════════════

    public long getUnreadCount(UUID sellerId) {
        return messageRepo.countUnreadMessagesForUser(sellerId, SenderType.SELLER);
    }

    // ═══════════════════════════════════════════════════════════
    // MARK CONVERSATION AS READ
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void markAsRead(UUID sellerId, UUID conversationId) {
        Conversations conversation = findConversationById(conversationId);

        if (!conversation.getSeller().getId().equals(sellerId)) {
            throw new ApiException("Unauthorized: this conversation does not belong to your store");
        }

        messageRepo.markAsReadBySenderType(conversationId, SenderType.USER);
        log.info("[Admin] Conversation [{}] marked as read by seller [{}]", conversationId, sellerId);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private Conversations findConversationById(UUID id) {
        return conversationsRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("[Admin] Conversation not found: {}", id);
                    return new ResourceNotFoundException("Conversation not found: " + id);
                });
    }

    private ProductImage resolveProductImage(Long imageId) {
        return productImageRepo.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
    }

    private ProductCardPayload buildProductCardPayload(Product product,
                                                       Conversations conversation,
                                                       User user) {
        String primaryImage = product.getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);

        return ProductCardPayload.builder()
                .conversationId(conversation.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productDescription(product.getProductDescription())
                .price(product.getDiscounted() ? product.getDiscountPrice() : product.getPrice())
                .originalPrice(product.getPrice())
                .isDiscounted(product.getDiscounted())
                .primaryImageUrl(primaryImage)
                .brand(product.getBrand())
                .stock(product.getStock())
                .buyerName(user.getFullName())
                .buyerEmail(user.getEmail())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private InboxUpdatePayload buildInboxUpdate(Conversations conversation, Message lastMsg) {
        String primaryImage = conversation.getProduct() != null
                ? conversation.getProduct().getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null)
                : null;

        return InboxUpdatePayload.builder()
                .conversationId(conversation.getId())
                .productName(conversation.getProduct() != null
                        ? conversation.getProduct().getName() : null)
                .productImageUrl(primaryImage)
                .lastMessage(lastMsg.getContent())
                .lastMessageAt(lastMsg.getCreatedAt())
                .senderType(lastMsg.getSenderType().name())
                .build();
    }

    private SellerInboxResponse mapToSellerInboxResponse(Conversations c,
                                                         Message lastMessage,
                                                         long unreadCount) {
        String primaryImage = c.getProduct() != null
                ? c.getProduct().getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null)
                : null;

        return SellerInboxResponse.builder()
                .conversationId(c.getId())
                .productId(c.getProduct() != null ? c.getProduct().getId() : null)
                .productName(c.getProduct() != null ? c.getProduct().getName() : null)
                .productDescription(c.getProduct() != null ? c.getProduct().getProductDescription() : null)
                .productPrice(c.getProduct() != null
                        ? (c.getProduct().getDiscounted()
                        ? c.getProduct().getDiscountPrice() : c.getProduct().getPrice()) : null)
                .productImageUrl(primaryImage)
                .isDiscounted(c.getProduct() != null && c.getProduct().getDiscounted())
                .buyerName(c.getUser().getFullName())
                .buyerEmail(c.getUser().getEmail())
                .lastMessage(lastMessage != null ? lastMessage.getContent() : null)
                .lastMessageAt(lastMessage != null ? lastMessage.getCreatedAt() : null)
                .unreadCount(unreadCount)
                .chatType(c.getChatType().name())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private ConversationResponse mapToConversationResponse(Conversations c) {
        Message lastMessage = messageRepo.findLatestMessage(c.getId());

        String primaryImage = c.getProduct() != null
                ? c.getProduct().getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null)
                : null;

        return ConversationResponse.builder()
                .id(c.getId())
                .userId(c.getUser().getId())
                .customerName(c.getUser().getFullName())
                .customerEmail(c.getUser().getEmail())
                .sellerId(c.getSeller().getId())
                .storeName(c.getSeller().getStoreName())
                .productId(c.getProduct() != null ? c.getProduct().getId() : null)
                .productName(c.getProduct() != null ? c.getProduct().getName() : null)
                .productImageUrl(primaryImage)
                .chatType(c.getChatType().name())
                .lastMessage(lastMessage != null ? lastMessage.getContent() : null)
                .lastMessageAt(lastMessage != null ? lastMessage.getCreatedAt() : null)
                .unreadCount(messageRepo.countByConversationsIdAndIsReadFalse(c.getId()))
                .createdAt(c.getCreatedAt())
                .build();
    }

    private MessageResponse mapToMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversations().getId())
                .senderType(m.getSenderType().name())
                .senderName(switch (m.getSenderType()) {
                    case USER   -> m.getUser().getFullName();
                    case SELLER -> m.getConversations().getSeller().getStoreName();
                    case SYSTEM -> "MarketPlace";
                })
                .content(m.getContent())
                .isProductCard(m.getContent() != null && m.getContent().startsWith("PRODUCT_CARD::"))
                .isAutomated(m.isAutomated())
                .productImageUrl(m.getProductImage() != null ? m.getProductImage().getImageUrl() : null)
                .isRead(m.isRead())
                .createdAt(m.getCreatedAt())
                .repliedAt(m.getRepliedAt())
                .build();
    }
}