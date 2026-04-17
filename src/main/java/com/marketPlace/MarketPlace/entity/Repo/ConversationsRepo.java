package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Conversations;
import com.marketPlace.MarketPlace.entity.Enums.ChatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationsRepo extends JpaRepository<Conversations, UUID> {

    // --- User conversations ---
    List<Conversations> findByUser_IdOrderByCreatedAtDesc(UUID userId);
    List<Conversations> findByUser_IdAndChatType(UUID userId, ChatType chatType);

    // --- Seller conversations ---
    List<Conversations> findBySeller_IdOrderByCreatedAtDesc(UUID sellerId);
    List<Conversations> findBySeller_IdAndChatType(UUID sellerId, ChatType chatType);

    boolean existsByOrder_IdAndSeller_Id(UUID orderId, UUID sellerId);

    // --- Check if conversation already exists between user and seller ---
    Optional<Conversations> findByUser_IdAndSeller_Id(UUID userId, UUID sellerId);
    boolean existsByUser_IdAndSeller_Id(UUID userId, UUID sellerId);

    // --- Check if conversation exists for a specific product ---
    Optional<Conversations> findByUser_IdAndSeller_IdAndProduct_Id(UUID userId, UUID sellerId, UUID productId);
    boolean existsByUser_IdAndSeller_IdAndProduct_Id(UUID userId, UUID sellerId, UUID productId);

    // --- Product-specific conversations (seller view) ---
    List<Conversations> findByProduct_Id(UUID productId);
    long countByProduct_Id(UUID productId);

    // --- Conversations by chat type ---
    List<Conversations> findByChatType(ChatType chatType);

    // --- Admin: conversations within date range ---
    @Query("""
            SELECT c FROM Conversations c
            WHERE c.createdAt BETWEEN :from AND :to
            ORDER BY c.createdAt DESC
            """)
    List<Conversations> findConversationsInDateRange(LocalDateTime from, LocalDateTime to);

    // --- Seller: conversations with unread messages ---
    @Query("""
            SELECT DISTINCT c FROM Conversations c
            JOIN Message m ON m.conversations.id = c.id
            WHERE c.seller.id = :sellerId
            AND m.isRead = false
            ORDER BY c.createdAt DESC
            """)
    List<Conversations> findSellerConversationsWithUnreadMessages(UUID sellerId);

    // --- User: conversations with unread messages ---
    @Query("""
            SELECT DISTINCT c FROM Conversations c
            JOIN Message m ON m.conversations.id = c.id
            WHERE c.user.id = :userId
            AND m.isRead = false
            ORDER BY c.createdAt DESC
            """)
    List<Conversations> findUserConversationsWithUnreadMessages(UUID userId);

    // --- Count total conversations per seller ---
    long countBySeller_Id(UUID sellerId);

    // --- Count total conversations per user ---
    long countByUser_Id(UUID userId);

    Optional<Conversations> findByOrder_IdAndSeller_Id(UUID orderId, UUID sellerId);
}