package com.aloute.repository.chat;

import com.aloute.model.chat.Message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Một trang tin CŨ HƠN con trỏ {@code (t, id)}, mới nhất trước; người gọi đảo lại thành thứ tự thời gian. */
    @Query("select m from Message m join fetch m.sender u join fetch u.profile left join fetch m.attachment "
            + "where m.conversation.id = :conversationId "
            + "and (m.createdAt < :t or (m.createdAt = :t and m.id < :id)) order by m.createdAt desc, m.id desc")
    List<Message> findOlder(@Param("conversationId") UUID conversationId, @Param("t") Instant t,
                            @Param("id") UUID id, Pageable pageable);

    /** Tin mới nhất của MỖI hội thoại trong {@code conversationIds}, dùng cho phần xem trước ở danh sách hội thoại. */
    @Query(value = """
            select distinct on (m.conversation_id) m.* from messages m
            where m.conversation_id in (:conversationIds)
            order by m.conversation_id, m.created_at desc""", nativeQuery = true)
    List<Message> findLatestForConversations(@Param("conversationIds") List<UUID> conversationIds);

    List<Message> findByConversationIdAndPinnedTrueOrderByCreatedAtDesc(UUID conversationId);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND lower(m.content) LIKE lower(concat('%', :keyword, '%')) ORDER BY m.createdAt DESC")
    List<Message> searchByContent(@Param("conversationId") UUID conversationId, @Param("keyword") String keyword);

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.attachment WHERE m.conversation.id = :conversationId AND m.attachment IS NOT NULL ORDER BY m.createdAt DESC")
    List<Message> findMediaMessages(@Param("conversationId") UUID conversationId);

    long countByConversationIdAndPinnedTrue(UUID conversationId);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND m.content LIKE '%http%' ORDER BY m.createdAt DESC")
    List<Message> findLinkMessages(@Param("conversationId") UUID conversationId);
    
    void deleteByConversationId(UUID conversationId);
}
