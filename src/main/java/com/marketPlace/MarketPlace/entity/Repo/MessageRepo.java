package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.Message;
import com.marketPlace.MarketPlace.entity.Enums.SenderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MessageRepo extends JpaRepository<Message, UUID> {

    // --- Conversation message feed ---
    List<Message> findByConversationsIdOrderByCreatedAtAsc(UUID conversationId);
    List<Message> findByConversationsIdOrderByCreatedAtDesc(UUID conversationId);

    // --- Paginated message fetch for large conversations ---
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversations.id = :conversationId
            AND m.createdAt < :before
            ORDER BY m.createdAt DESC
            """)
    List<Message> findMessagesBefore(UUID conversationId, LocalDateTime before);

    // --- Unread messages ---
    List<Message> findByConversationsIdAndIsReadFalse(UUID conversationId);
    long countByConversationsIdAndIsReadFalse(UUID conversationId);

    // --- Unread count for a specific user across all conversations ---
    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.user.id = :userId
            AND m.isRead = false
            AND m.senderType <> :senderType
            """)
    long countUnreadMessagesForUser(UUID userId, SenderType senderType);

    // --- Mark all messages in a conversation as read ---
    @Modifying
    @Transactional
    @Query("""
            UPDATE Message m SET m.isRead = true
            WHERE m.conversations.id = :conversationId
            AND m.isRead = false
            """)
    void markAllAsReadInConversation(UUID conversationId);

    // --- Mark all messages as read for a specific sender type ---
    @Modifying
    @Transactional
    @Query("""
            UPDATE Message m SET m.isRead = true
            WHERE m.conversations.id = :conversationId
            AND m.senderType = :senderType
            AND m.isRead = false
            """)
    void markAsReadBySenderType(UUID conversationId, SenderType senderType);

    // --- Latest message in a conversation (for conversation preview) ---
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversations.id = :conversationId
            ORDER BY m.createdAt DESC
            LIMIT 1
            """)
    Message findLatestMessage(UUID conversationId);

    // --- All messages by a user ---
    List<Message> findByUserId(UUID userId);

    // --- Messages filtered by sender type in a conversation ---
    List<Message> findByConversationsIdAndSenderType(UUID conversationId, SenderType senderType);

    // --- Messages containing an image ---
    @Query("SELECT m FROM Message m WHERE m.conversations.id = :conversationId AND m.productImage IS NOT NULL")
    List<Message> findMediaMessages(UUID conversationId);

    // --- Delete all messages in a conversation ---
    @Modifying
    @Transactional
    void deleteByConversationsId(UUID conversationId);
}