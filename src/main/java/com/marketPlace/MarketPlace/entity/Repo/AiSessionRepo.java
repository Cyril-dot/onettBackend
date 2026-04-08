package com.marketPlace.MarketPlace.entity.Repo;

import com.marketPlace.MarketPlace.entity.AiSession;
import com.marketPlace.MarketPlace.entity.Enums.SessionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSessionRepo extends JpaRepository<AiSession, UUID> {

    // --- User session history ---
    List<AiSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<AiSession> findByUserIdAndSessionType(UUID userId, SessionType sessionType);
    Optional<AiSession> findTopByUserIdOrderByLastActiveDesc(UUID userId);
    long countByUserId(UUID userId);

    // --- Session type queries ---
    List<AiSession> findBySessionType(SessionType sessionType);
    long countBySessionType(SessionType sessionType);

    // --- Active session management ---
    @Query("""
            SELECT s FROM AiSession s
            WHERE s.user.id = :userId
            AND s.lastActive >= :since
            ORDER BY s.lastActive DESC
            """)
    List<AiSession> findActiveSessionsByUser(UUID userId, LocalDateTime since);

    // --- Update last active timestamp ---
    @Modifying
    @Transactional
    @Query("UPDATE AiSession s SET s.lastActive = :lastActive WHERE s.id = :sessionId")
    void updateLastActive(UUID sessionId, LocalDateTime lastActive);

    // --- Budget-based queries ---
    List<AiSession> findByUserIdAndBudgetGreaterThanEqual(UUID userId, BigDecimal minBudget);
    List<AiSession> findByUserIdAndBudgetBetween(UUID userId, BigDecimal min, BigDecimal max);

    @Query("""
            SELECT AVG(s.budget) FROM AiSession s
            WHERE s.user.id = :userId
            AND s.sessionType = :sessionType
            """)
    BigDecimal calculateAverageBudgetByUserAndType(UUID userId, SessionType sessionType);

    // --- Stale session cleanup ---
    @Query("SELECT s FROM AiSession s WHERE s.lastActive < :cutoff")
    List<AiSession> findStaleSessions(LocalDateTime cutoff);

    @Modifying
    @Transactional
    void deleteByLastActiveBefore(LocalDateTime cutoff);

    // --- Delete all sessions for a user (GDPR/account deletion) ---
    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);

    // --- Admin analytics: sessions in date range ---
    @Query("""
            SELECT s FROM AiSession s
            WHERE s.createdAt BETWEEN :from AND :to
            ORDER BY s.createdAt DESC
            """)
    List<AiSession> findSessionsInDateRange(LocalDateTime from, LocalDateTime to);

    // --- Admin analytics: session count by type in date range ---
    @Query("""
            SELECT s.sessionType, COUNT(s) FROM AiSession s
            WHERE s.createdAt BETWEEN :from AND :to
            GROUP BY s.sessionType
            """)
    List<Object[]> countSessionsByTypeInDateRange(LocalDateTime from, LocalDateTime to);
}