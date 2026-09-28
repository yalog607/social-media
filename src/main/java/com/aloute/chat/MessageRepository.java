package com.aloute.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("select m from Message m join fetch m.sender u join fetch u.profile left join fetch m.attachment "
            + "where m.conversation.id = :conversationId order by m.createdAt")
    List<Message> findByConversation(@Param("conversationId") UUID conversationId);

    /** Tin mới nhất của MỖI hội thoại trong {@code conversationIds}, dùng cho phần xem trước ở danh sách hội thoại. */
    @Query(value = """
            select distinct on (m.conversation_id) m.* from messages m
            where m.conversation_id in (:conversationIds)
            order by m.conversation_id, m.created_at desc""", nativeQuery = true)
    List<Message> findLatestForConversations(@Param("conversationIds") List<UUID> conversationIds);
}
