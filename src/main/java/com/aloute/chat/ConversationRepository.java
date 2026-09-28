package com.aloute.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByDirectKey(String directKey);

    @Query("select c from Conversation c where c.id = :id and c.type = com.aloute.chat.ConversationType.GROUP")
    Optional<Conversation> findGroupById(@Param("id") UUID id);
}
