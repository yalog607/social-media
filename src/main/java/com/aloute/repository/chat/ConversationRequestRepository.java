package com.aloute.repository.chat;

import com.aloute.model.chat.ConversationRequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationRequestRepository extends JpaRepository<ConversationRequest, ConversationRequest.ConversationRequestId> {

    @Query("SELECT r FROM ConversationRequest r JOIN FETCH r.user u JOIN FETCH u.profile JOIN FETCH r.inviter i JOIN FETCH i.profile WHERE r.conversation.id = :conversationId ORDER BY r.createdAt ASC")
    List<ConversationRequest> findByConversationIdWithUsers(@Param("conversationId") UUID conversationId);

    void deleteByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Query("SELECT r FROM ConversationRequest r WHERE r.conversation.id = :conversationId AND r.user.id = :userId")
    java.util.Optional<ConversationRequest> findByConversationIdAndUserId(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
    
    void deleteByConversationId(UUID conversationId);
    
    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Query("SELECT r FROM ConversationRequest r WHERE r.user.id = :userId")
    List<ConversationRequest> findByUserId(@Param("userId") UUID userId);
}
