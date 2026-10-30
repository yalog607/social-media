package com.aloute.repository.chat;

import com.aloute.model.chat.Conversation;
import com.aloute.model.chat.ConversationType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByDirectKey(String directKey);

    java.util.List<Conversation> findByDirectKeyIn(java.util.Collection<String> directKeys);

    @Query("select c from Conversation c where c.id = :id and c.type = com.aloute.model.chat.ConversationType.GROUP")
    Optional<Conversation> findGroupById(@Param("id") UUID id);

    @Query("select c from Conversation c where c.type = com.aloute.model.chat.ConversationType.GROUP order by c.createdAt desc")
    java.util.List<Conversation> findAllGroupConversations();

    @Query("select c from Conversation c where c.type = com.aloute.model.chat.ConversationType.GROUP and c.socialGroup = true order by c.createdAt desc")
    java.util.List<Conversation> findAllSocialGroups();

    @Query("select c from Conversation c where c.id = :id and c.type = com.aloute.model.chat.ConversationType.GROUP and c.socialGroup = true")
    Optional<Conversation> findSocialGroupById(@Param("id") UUID id);
}
