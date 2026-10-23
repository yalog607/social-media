package com.aloute.repository.chat;

import com.aloute.model.chat.ConversationMember;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    void deleteByConversationIdAndUserId(UUID conversationId, UUID userId);

    void deleteByConversationId(UUID conversationId);

    long countByConversationId(UUID conversationId);

    @Query("select cm from ConversationMember cm join fetch cm.user u join fetch u.profile "
            + "where cm.conversation.id = :conversationId order by cm.joinedAt")
    List<ConversationMember> findMembers(@Param("conversationId") UUID conversationId);

    @Query("select cm from ConversationMember cm join fetch cm.conversation c where cm.user.id = :userId")
    List<ConversationMember> findWithConversationForUser(@Param("userId") UUID userId);

    interface UnreadRow {
        UUID getConversationId();

        long getUnread();
    }

    /** Số tin chưa đọc theo TỪNG hội thoại của {@code userId} (không tính tin do chính mình gửi). */
    @Query(value = """
            select cm.conversation_id as conversationId, count(m.id) as unread
            from conversation_members cm
            join messages m on m.conversation_id = cm.conversation_id
            where cm.user_id = :userId
              and m.sender_id <> :userId
              and m.created_at > coalesce(cm.last_read_at, cm.joined_at)
            group by cm.conversation_id
            """, nativeQuery = true)
    List<UnreadRow> unreadCounts(@Param("userId") UUID userId);

    /** Tổng số tin chưa đọc trên mọi hội thoại, dùng cho huy hiệu ở thanh điều hướng. */
    @Query(value = """
            select coalesce(sum(t.unread), 0) from (
                select count(m.id) as unread
                from conversation_members cm
                join messages m on m.conversation_id = cm.conversation_id
                where cm.user_id = :userId
                  and m.sender_id <> :userId
                  and m.created_at > coalesce(cm.last_read_at, cm.joined_at)
                group by cm.conversation_id
            ) t
            """, nativeQuery = true)
    long totalUnread(@Param("userId") UUID userId);
}
