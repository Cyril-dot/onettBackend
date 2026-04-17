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
public class ChatService {

    private final ConversationsRepo     conversationsRepo;
    private final MessageRepo           messageRepo;
    private final ProductRepo           productRepo;
    private final UserRepo              userRepo;
    private final SellerRepo            sellerRepo;
    private final ProductImageRepo      productImageRepo;
    private final OrderRepo             orderRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService   notificationService;

    // ═══════════════════════════════════════════════════════════
    // POST-PURCHASE: CREATE ORDER CHAT
    // Called by PaymentService after payment succeeds.
    // Creates a chat per seller for each set of order items.
    // Sends an automated delivery details request to the user.
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void createOrderChat(UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        User user = order.getUser();

        Map<Seller, List<OrderItem>> itemsBySeller = order.getOrderItems().stream()
                .collect(Collectors.groupingBy(item -> item.getProduct().getSeller()));

        log.info("Creating order chats for order [{}] — {} seller(s) involved",
                orderId, itemsBySeller.size());

        for (Map.Entry<Seller, List<OrderItem>> entry : itemsBySeller.entrySet()) {
            Seller          seller      = entry.getKey();
            List<OrderItem> sellerItems = entry.getValue();

            boolean chatExists = conversationsRepo
                    .existsByOrder_IdAndSeller_Id(orderId, seller.getId());

            if (chatExists) {
                log.info("Chat already exists for order [{}] + seller [{}] — skipping",
                        orderId, seller.getId());
                continue;
            }

            Product primaryProduct = sellerItems.getFirst().getProduct();

            Conversations conversation = Conversations.builder()
                    .user(user)
                    .seller(seller)
                    .product(primaryProduct)
                    .order(order)
                    .chatType(ChatType.ORDER_SUPPORT)
                    .build();

            Conversations saved = conversationsRepo.save(conversation);

            // ── Automated order summary message ──────────────────────────
            Message summaryMessage = Message.builder()
                    .conversations(saved)
                    .user(user)
                    .senderType(SenderType.SYSTEM)
                    .content(buildOrderSummaryMessage(order, sellerItems, seller))
                    .isAutomated(true)
                    .build();
            messageRepo.save(summaryMessage);

            // ── Automated delivery details request ───────────────────────
            Message deliveryMessage = Message.builder()
                    .conversations(saved)
                    .user(user)
                    .senderType(SenderType.SYSTEM)
                    .content(buildDeliveryDetailsPrompt(user))
                    .isAutomated(true)
                    .build();
            messageRepo.save(deliveryMessage);

            // ── Push to user via WebSocket ───────────────────────────────
            messagingTemplate.convertAndSend(
                    "/topic/user/" + user.getId() + "/conversation/" + saved.getId(),
                    mapToMessageResponse(summaryMessage)
            );
            messagingTemplate.convertAndSend(
                    "/topic/user/" + user.getId() + "/conversation/" + saved.getId(),
                    mapToMessageResponse(deliveryMessage)
            );

            // ── Push to seller inbox ─────────────────────────────────────
            messagingTemplate.convertAndSend(
                    "/topic/seller/" + seller.getId() + "/inbox",
                    buildOrderChatInboxPayload(saved, order, sellerItems)
            );

            // ── Notify seller via email: new order chat opened ───────────
            String productNames = sellerItems.stream()
                    .map(i -> i.getProduct().getName())
                    .collect(Collectors.joining(", "));
            notificationService.notifyNewChatMessageToSeller(
                    seller,
                    user.getFullName(),
                    saved.getId(),
                    "New order from " + user.getFullName() + " — " + productNames
            );

            log.info("Order chat [{}] created for order [{}] + seller [{}] — automated messages sent",
                    saved.getId(), orderId, seller.getStoreName());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // USER SUBMITS DELIVERY DETAILS (via chat reply)
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public MessageResponse submitDeliveryDetails(UUID userId,
                                                 UUID conversationId,
                                                 DeliveryDetailsRequest request) {
        Conversations conversation = findConversationById(conversationId);

        if (!conversation.getUser().getId().equals(userId)) {
            throw new ApiException("Unauthorized: this conversation does not belong to you");
        }

        Message message = Message.builder()
                .conversations(conversation)
                .user(conversation.getUser())
                .senderType(SenderType.USER)
                .content(formatDeliveryDetails(request))
                .isAutomated(false)
                .build();

        Message saved = messageRepo.save(message);

        Message confirmMsg = Message.builder()
                .conversations(conversation)
                .user(conversation.getUser())
                .senderType(SenderType.SYSTEM)
                .content("✅ Delivery details received! The seller will be in touch to coordinate delivery.")
                .isAutomated(true)
                .build();
        messageRepo.save(confirmMsg);

        String sellerTopic = "/topic/seller/" + conversation.getSeller().getId()
                + "/conversation/" + conversationId;
        String userTopic   = "/topic/user/" + userId + "/conversation/" + conversationId;

        messagingTemplate.convertAndSend(sellerTopic, mapToMessageResponse(saved));
        messagingTemplate.convertAndSend(userTopic, mapToMessageResponse(confirmMsg));
        messagingTemplate.convertAndSend(
                "/topic/seller/" + conversation.getSeller().getId() + "/inbox/update",
                buildInboxUpdate(conversation, saved)
        );

        // ── Notify seller via email: delivery details submitted ──────────
        notificationService.notifyNewChatMessageToSeller(
                conversation.getSeller(),
                conversation.getUser().getFullName(),
                conversationId,
                "Delivery details submitted — ready to coordinate shipment"
        );

        log.info("User [{}] submitted delivery details for conversation [{}]",
                conversation.getUser().getEmail(), conversationId);

        return mapToMessageResponse(saved);
    }

    // ═══════════════════════════════════════════════════════════
    // START CONVERSATION (product enquiry — pre-purchase)
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public ConversationResponse startConversation(UUID userId, StartConversationRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Product product = productRepo.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Seller seller = product.getSeller();
        if (seller == null) throw new ApiException("Product has no seller assigned");

        log.info("User [{}] starting conversation about product [{}]",
                user.getEmail(), product.getName());

        return conversationsRepo.findByUser_IdAndSeller_IdAndProduct_Id(
                        userId, seller.getId(), request.getProductId())
                .map(existing -> {
                    log.info("Existing conversation found: [{}]", existing.getId());
                    return mapToConversationResponse(existing);
                })
                .orElseGet(() -> {
                    Conversations conversation = Conversations.builder()
                            .user(user)
                            .product(product)
                            .seller(seller)
                            .chatType(ChatType.BUYER_SELLER)
                            .build();

                    Conversations saved = conversationsRepo.save(conversation);

                    Message productCardMessage = Message.builder()
                            .conversations(saved)
                            .user(user)
                            .senderType(SenderType.USER)
                            .content(buildProductCardContent(product))
                            .isAutomated(false)
                            .build();
                    messageRepo.save(productCardMessage);

                    messagingTemplate.convertAndSend(
                            "/topic/seller/" + seller.getId() + "/inbox",
                            buildProductCardPayload(product, saved, user)
                    );

                    // ── Notify seller via email: new buyer enquiry ───────────
                    notificationService.notifyNewChatMessageToSeller(
                            seller,
                            user.getFullName(),
                            saved.getId(),
                            user.getFullName() + " is enquiring about " + product.getName()
                    );

                    log.info("New conversation [{}] started — product: [{}] pushed to seller [{}]",
                            saved.getId(), product.getName(), seller.getStoreName());

                    return mapToConversationResponse(saved);
                });
    }

    // ═══════════════════════════════════════════════════════════
    // USER SENDS MESSAGE
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public MessageResponse userSendMessage(UUID userId,
                                           UUID conversationId,
                                           SendMessageRequest request) {
        Conversations conversation = findConversationById(conversationId);

        if (!conversation.getUser().getId().equals(userId)) {
            log.warn("Unauthorized message attempt — user [{}] on conversation [{}]",
                    userId, conversationId);
            throw new ApiException("Unauthorized: this conversation does not belong to you");
        }

        Message message = Message.builder()
                .conversations(conversation)
                .user(conversation.getUser())
                .senderType(SenderType.USER)
                .content(request.getContent())
                .isAutomated(false)
                .productImage(request.getProductImageId() != null
                        ? resolveProductImage(request.getProductImageId()) : null)
                .build();

        Message saved = messageRepo.save(message);
        MessageResponse response = mapToMessageResponse(saved);

        messagingTemplate.convertAndSend(
                "/topic/seller/" + conversation.getSeller().getId()
                        + "/conversation/" + conversationId,
                response
        );
        messagingTemplate.convertAndSend(
                "/topic/seller/" + conversation.getSeller().getId() + "/inbox/update",
                buildInboxUpdate(conversation, saved)
        );

        // ── Notify seller via email: new message from buyer ──────────────
        notificationService.notifyNewChatMessageToSeller(
                conversation.getSeller(),
                conversation.getUser().getFullName(),
                conversationId,
                request.getContent()
        );

        log.info("User [{}] sent message in conversation [{}]",
                conversation.getUser().getEmail(), conversationId);
        return response;
    }

    // ═══════════════════════════════════════════════════════════
    // SELLER SENDS REPLY
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public MessageResponse sellerSendMessage(UUID sellerId,
                                             UUID conversationId,
                                             SendMessageRequest request) {
        Conversations conversation = findConversationById(conversationId);

        if (!conversation.getSeller().getId().equals(sellerId)) {
            log.warn("Unauthorized reply attempt — seller [{}] on conversation [{}]",
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

        // ── Notify buyer via FCM + in-app: seller replied ────────────────
        notificationService.notifyNewChatMessage(
                conversation.getUser(),
                seller.getStoreName(),
                conversationId,
                request.getContent()
        );

        log.info("Seller [{}] replied in conversation [{}]",
                seller.getStoreName(), conversationId);
        return response;
    }

    // ═══════════════════════════════════════════════════════════
    // GET SELLER INBOX
    // ═══════════════════════════════════════════════════════════

    public List<SellerInboxResponse> getSellerInbox(UUID sellerId) {
        log.info("Loading inbox for seller [{}]", sellerId);

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
    // GET FULL CHAT HISTORY
    // ═══════════════════════════════════════════════════════════

    public ChatHistoryResponse getChatHistory(UUID conversationId, UUID viewerId) {
        Conversations conversation = findConversationById(conversationId);

        log.info("Fetching chat history for conversation [{}]", conversationId);

        boolean isSeller = conversation.getSeller().getId().equals(viewerId);
        if (isSeller) {
            messageRepo.markAsReadBySenderType(conversationId, SenderType.USER);
            log.info("Seller opened conversation [{}] — marked user messages as read", conversationId);
        } else {
            messageRepo.markAsReadBySenderType(conversationId, SenderType.SELLER);
            log.info("User opened conversation [{}] — marked seller messages as read", conversationId);
        }

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
    // GET USER CONVERSATIONS
    // ═══════════════════════════════════════════════════════════

    public List<ConversationResponse> getUserConversations(UUID userId) {
        log.info("Fetching conversations for user [{}]", userId);
        return conversationsRepo.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }

    public List<ConversationResponse> getUserConversationsWithUnread(UUID userId) {
        return conversationsRepo.findUserConversationsWithUnreadMessages(userId)
                .stream().map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // GET SELLER CONVERSATIONS
    // ═══════════════════════════════════════════════════════════

    public List<ConversationResponse> getSellerConversations(UUID sellerId) {
        return conversationsRepo.findBySeller_IdOrderByCreatedAtDesc(sellerId)
                .stream().map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }

    public List<ConversationResponse> getSellerConversationsWithUnread(UUID sellerId) {
        return conversationsRepo.findSellerConversationsWithUnreadMessages(sellerId)
                .stream().map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════
    // UNREAD COUNTS
    // ═══════════════════════════════════════════════════════════

    public long getUnreadCountForUser(UUID userId) {
        return messageRepo.countUnreadMessagesForUser(userId, SenderType.USER);
    }

    public long getUnreadCountForSeller(UUID sellerId) {
        return messageRepo.countUnreadMessagesForUser(sellerId, SenderType.SELLER);
    }

    // ═══════════════════════════════════════════════════════════
    // MARK AS READ
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void markConversationAsRead(UUID conversationId, SenderType readBy) {
        messageRepo.markAsReadBySenderType(conversationId, readBy);
        log.info("Conversation [{}] marked as read by [{}]", conversationId, readBy);
    }

    // ═══════════════════════════════════════════════════════════
    // DELETE CONVERSATION
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void deleteConversation(UUID conversationId, UUID userId) {
        Conversations conversation = findConversationById(conversationId);

        if (!conversation.getUser().getId().equals(userId)) {
            throw new ApiException("Unauthorized: cannot delete this conversation");
        }

        messageRepo.deleteByConversationsId(conversationId);
        conversationsRepo.delete(conversation);
        log.info("Conversation [{}] deleted by user [{}]", conversationId, userId);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private Conversations findConversationById(UUID id) {
        return conversationsRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("Conversation not found: {}", id);
                    return new ResourceNotFoundException("Conversation not found: " + id);
                });
    }

    private ProductImage resolveProductImage(Long imageId) {
        return productImageRepo.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
    }

    private String buildProductCardContent(Product product) {
        String primaryImage = product.getImages().stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse("");

        return "PRODUCT_CARD::%s::GHS %s::%s::%s".formatted(
                product.getName(),
                product.getDiscounted() ? product.getDiscountPrice() : product.getPrice(),
                product.getProductDescription() != null ? product.getProductDescription() : "No description",
                primaryImage
        );
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

    private Object buildOrderChatInboxPayload(Conversations conversation,
                                              Order order,
                                              List<OrderItem> items) {
        String productNames = items.stream()
                .map(i -> i.getProduct().getName())
                .collect(Collectors.joining(", "));

        return InboxUpdatePayload.builder()
                .conversationId(conversation.getId())
                .productName("Order #" + order.getId().toString().substring(0, 8).toUpperCase()
                        + " — " + productNames)
                .lastMessage("New order placed — delivery details requested from customer")
                .lastMessageAt(LocalDateTime.now())
                .senderType(SenderType.SYSTEM.name())
                .build();
    }

    private String buildOrderSummaryMessage(Order order, List<OrderItem> items, Seller seller) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎉 *Payment confirmed! Your order has been placed.*\n\n");
        sb.append("📦 *Order ID:* ").append(order.getId().toString().substring(0, 8).toUpperCase()).append("\n");
        sb.append("🏪 *Seller:* ").append(seller.getStoreName()).append("\n\n");
        sb.append("*Items ordered:*\n");
        for (OrderItem item : items) {
            sb.append("• ").append(item.getProduct().getName())
                    .append(" x").append(item.getQuantity())
                    .append(" — GHS ").append(item.getSubTotal()).append("\n");
        }
        sb.append("\n*Total: GHS ").append(order.getTotal()).append("*");
        return sb.toString();
    }

    private String buildDeliveryDetailsPrompt(User user) {
        return """
                Hi %s! 👋 To complete your delivery, please provide the following details:

                1. *Full Name* — as it should appear on the package
                2. *Email Address* — for delivery updates
                3. *Active Phone Number* — for the delivery driver to call
                4. *Active WhatsApp Number* — for delivery coordination
                5. *Nearest Landmark* — e.g. "near Kumasi Central Market" or "opposite Ghana Post Office"
                6. *Your Location / Area* — town, suburb, or neighbourhood
                7. *GPS Address* (optional) — e.g. GhanaPostGPS or Google Maps pin link

                Please reply with all the above details so your order can be delivered promptly. 📍
                """.formatted(user.getFullName());
    }

    private String formatDeliveryDetails(DeliveryDetailsRequest req) {
        return """
                📋 *Delivery Details Submitted*

                👤 Full Name: %s
                📧 Email: %s
                📞 Phone: %s
                💬 WhatsApp: %s
                📍 Landmark: %s
                🏘 Location: %s
                🗺 GPS: %s
                """.formatted(
                req.getFullName(),
                req.getEmail(),
                req.getPhoneNumber(),
                req.getWhatsAppNumber(),
                req.getLandmark(),
                req.getLocation(),
                req.getGpsAddress() != null ? req.getGpsAddress() : "Not provided"
        );
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