package com.aloute.notification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n from Notification n
            join fetch n.actor a join fetch a.profile
            left join fetch n.post
            where n.recipient.id = :recipientId
            order by n.createdAt desc""")
    List<Notification> findRecentFor(@Param("recipientId") UUID recipientId, Limit limit);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :recipientId and n.readAt is null")
    void markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

    void deleteByConversationId(UUID conversationId);
}
