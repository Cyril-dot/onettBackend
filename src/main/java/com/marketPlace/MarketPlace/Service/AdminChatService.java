package com.marketPlace.MarketPlace.Service;

import com.marketPlace.MarketPlace.dtos.*;
import com.marketPlace.MarketPlace.entity.*;
import com.marketPlace.MarketPlace.entity.Enums.ChatType;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private final ConversationsRepo     conversationsRepo;
    private final MessageRepo           messageRepo;
    private final ProductImageRepo      productImageRepo;
    private final SellerRepo            sellerRepo;
    private final OrderRepo             orderRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService   notificationService;

    // ═══════════════════════════════════════════════════════════
    // GET SELLER INBOX
    // ═══════════════════════════════════════════════════════════

    public List<SellerInboxResponse> getInbox(UUID sellerId) {
        log.info("[Admin] Loading inbox for seller [{}]", sellerId);

        return conversationsRepo.findBySeller_IdOrderByCreatedAtDesc(sellerId)
                .stream()
                .map(c -> {
                    Message lastMessage = messageRepo.findLatestMessage(c.getId());
                    long    unread      = messageRepo.countByConversationsIdAndIsReadFalse(c.getId());
                    return mapToSellerInboxResponse(c, lastMessage, unread);
                })
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // GET SELLER CONVERSATIONS
    // ═══════════════════════════════════════════════════════════

    public List<ConversationResponse> getConversations(UUID sellerId, boolean unreadOnly) {
        log.info("[Admin] Fetching conversations for seller [{}] — unreadOnly={}", sellerId, unreadOnly);

        List<Conversations> conversations = unreadOnly
                ? conversationsRepo.findSellerConversationsWithUnreadMessages(sellerId)
                : conversationsRepo.findBySeller_IdOrderByCreatedAtDesc(sellerId);

        return conversations.stream()
                .map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // GET CHAT HISTORY
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public ChatHistoryResponse getChatHistory(UUID sellerId, UUID conversationId) {
        Conversations conversation = findConversationById(conversationId);

        if (!conversation.getSeller().getId().equals(sellerId)) {
            log.warn("[Admin] Seller [{}] attempted to access conversation [{}] — DENIED",
                    sellerId, conversationId);
            throw new ApiException("Unauthorized: this conversation does not belong to your store");
        }

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

        messagingTemplate.convertAndSend(
                "/topic/user/" + conversation.getUser().getId()
                        + "/conversation/" + conversationId,
                response
        );
        messagingTemplate.convertAndSend(
                "/topic/user/" + conversation.getUser().getId() + "/inbox/update",
                buildInboxUpdate(conversation, saved)
        );

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
    // SELLER STARTS ORDER CONVERSATION
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public ConversationResponse startOrderConversation(UUID sellerId, UUID orderId) {
        Seller seller = sellerRepo.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found: " + sellerId));

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        List<OrderItem> sellerItems = order.getOrderItems().stream()
                .filter(item -> item.getProduct() != null
                        && item.getProduct().getSeller() != null
                        && item.getProduct().getSeller().getId().equals(sellerId))
                .collect(Collectors.toList());

        if (sellerItems.isEmpty()) {
            throw new ApiException(
                    "Unauthorized: this order contains no products from your store");
        }

        Optional<Conversations> existing =
                conversationsRepo.findByOrder_IdAndSeller_Id(orderId, sellerId);

        if (existing.isPresent()) {
            log.info("[Admin] Seller [{}] re-opened existing order conversation [{}] for order [{}]",
                    seller.getStoreName(), existing.get().getId(), orderId);
            return mapToConversationResponse(existing.get());
        }

        Product primaryProduct = sellerItems.getFirst().getProduct();
        User    buyer          = order.getUser();

        Conversations conversation = Conversations.builder()
                .user(buyer)
                .seller(seller)
                .product(primaryProduct)
                .order(order)
                .chatType(ChatType.ORDER_SUPPORT)
                .build();

        Conversations saved = conversationsRepo.save(conversation);

        String orderIdShort = orderId.toString().substring(0, 8).toUpperCase();
        String primaryImage = getPrimaryImageUrl(primaryProduct);
        String itemsSummary = sellerItems.stream()
                .map(i -> i.getProduct().getName() + " x" + i.getQuantity())
                .collect(Collectors.joining(", "));

        String orderCardContent = "ORDER_CARD::%s::%s::%s::%s::%s::%s::%s".formatted(
                orderIdShort,
                primaryProduct.getName(),
                primaryImage != null ? primaryImage : "",
                sellerItems.getFirst().getUnitPrice(),
                order.getTotal(),
                order.getOrderStatus().name(),
                itemsSummary
        );

        Message orderCardMsg = Message.builder()
                .conversations(saved)
                .user(buyer)
                .senderType(SenderType.SYSTEM)
                .content(orderCardContent)
                .isAutomated(true)
                .build();
        messageRepo.save(orderCardMsg);

        String greeting = buildSellerGreeting(seller, buyer, order, sellerItems);

        Message greetingMsg = Message.builder()
                .conversations(saved)
                .user(buyer)
                .senderType(SenderType.SELLER)
                .content(greeting)
                .isAutomated(false)
                .repliedAt(LocalDateTime.now())
                .build();
        messageRepo.save(greetingMsg);

        messagingTemplate.convertAndSend(
                "/topic/user/" + buyer.getId() + "/conversation/" + saved.getId(),
                mapToMessageResponse(orderCardMsg)
        );
        messagingTemplate.convertAndSend(
                "/topic/user/" + buyer.getId() + "/conversation/" + saved.getId(),
                mapToMessageResponse(greetingMsg)
        );
        messagingTemplate.convertAndSend(
                "/topic/user/" + buyer.getId() + "/inbox/update",
                buildInboxUpdate(saved, greetingMsg)
        );

        notificationService.notifyNewChatMessage(
                buyer,
                seller.getStoreName(),
                saved.getId(),
                greeting
        );

        log.info("[Admin] Seller [{}] started order conversation [{}] for order [{}] with buyer [{}]",
                seller.getStoreName(), saved.getId(), orderIdShort, buyer.getEmail());

        return mapToConversationResponse(saved);
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

    private String getPrimaryImageUrl(Product product) {
        if (product == null || product.getImages() == null) return null;
        return product.getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }

    private String buildSellerGreeting(Seller seller,
                                       User buyer,
                                       Order order,
                                       List<OrderItem> sellerItems) {
        String orderIdShort = order.getId().toString().substring(0, 8).toUpperCase();
        String productNames = sellerItems.stream()
                .map(i -> i.getProduct().getName())
                .collect(Collectors.joining(", "));

        return """
                Hi %s! 👋 This is %s reaching out about your recent order #%s.

                You've ordered: %s

                I wanted to get in touch to help coordinate your delivery and answer any questions you might have. Please feel free to reply here!
                """.formatted(
                buyer.getFullName().split(" ")[0],
                seller.getStoreName(),
                orderIdShort,
                productNames
        );
    }

    private ProductCardPayload buildProductCardPayload(Product product,
                                                       Conversations conversation,
                                                       User user) {
        String primaryImage = getPrimaryImageUrl(product);

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
                ? getPrimaryImageUrl(conversation.getProduct())
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
        String primaryImage = c.getProduct() != null ? getPrimaryImageUrl(c.getProduct()) : null;

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
        String primaryImage = c.getProduct() != null ? getPrimaryImageUrl(c.getProduct()) : null;

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