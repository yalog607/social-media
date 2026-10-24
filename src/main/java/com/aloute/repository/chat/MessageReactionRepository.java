package com.aloute.repository.chat;

import com.aloute.model.chat.MessageReaction;
import com.aloute.model.reaction.ReactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {

    Optional<MessageReaction> findByMessageIdAndUserId(UUID messageId, UUID userId);

    /** Số lượng mỗi loại cảm xúc cho danh sách tin nhắn trong MỘT truy vấn. */
    @Query("""
            select r.message.id as messageId, r.type as type, count(r) as total
            from MessageReaction r where r.message.id in :messageIds
            group by r.message.id, r.type""")
    List<MessageReactionCount> countsByMessageIds(@Param("messageIds") Collection<UUID> messageIds);

    /** Cảm xúc mà {@code userId} đã thả cho danh sách tin nhắn trong MỘT truy vấn. */
    @Query("select r.message.id as messageId, r.type as type from MessageReaction r where r.message.id in :messageIds and r.user.id = :userId")
    List<MyMessageReaction> myReactions(@Param("messageIds") Collection<UUID> messageIds, @Param("userId") UUID userId);

    /** Người đã thả cảm xúc cho một tin nhắn (còn hoạt động), mới nhất trước. */
    @Query("""
            select r from MessageReaction r join fetch r.user u join fetch u.profile
            where r.message.id = :messageId and u.status = com.aloute.model.user.UserStatus.ACTIVE
            order by r.createdAt desc, r.id desc""")
    List<MessageReaction> findReactors(@Param("messageId") UUID messageId, Pageable pageable);

    interface MessageReactionCount {
        UUID getMessageId();

        ReactionType getType();

        long getTotal();
    }

    interface MyMessageReaction {
        UUID getMessageId();

        ReactionType getType();
    }
}
