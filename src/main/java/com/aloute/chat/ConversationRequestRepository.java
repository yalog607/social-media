package com.aloute.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationRequestRepository extends JpaRepository<ConversationRequest, ConversationRequest.ConversationRequestId> {

    @Query("SELECT r FROM ConversationRequest r JOIN FETCH r.user u JOIN FETCH u.profile JOIN FETCH r.inviter i JOIN FETCH i.profile WHERE r.conversation.id = :conversationId ORDER BY r.createdAt ASC")
    List<ConversationRequest> findByConversationIdWithUsers(@Param("conversationId") UUID conversationId);

    void deleteByConversationIdAndUserId(UUID conversationId, UUID userId);
    
    void deleteByConversationId(UUID conversationId);
    
    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);
}
