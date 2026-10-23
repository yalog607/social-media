package com.aloute.repository.notification;

import com.aloute.model.notification.Notification;
import com.aloute.model.notification.NotificationType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n join fetch n.actor a join fetch a.profile "
            + "left join fetch n.post left join fetch n.post.author left join fetch n.post.author.profile "
            + "where n.recipient.id = :recipientId order by n.createdAt desc")
    List<Notification> findRecentFor(@Param("recipientId") UUID recipientId, Limit limit);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :recipientId and n.readAt is null and n.type not in :excludedTypes")
    void markAllNormalRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now, @Param("excludedTypes") Collection<NotificationType> excludedTypes);

    void deleteByConversationId(UUID conversationId);
}
