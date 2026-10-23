package com.aloute.model.chat;

import com.aloute.model.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "conversation_requests")
@IdClass(ConversationRequest.ConversationRequestId.class)
@Getter
@Setter
@NoArgsConstructor
public class ConversationRequest {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ConversationRequest(Conversation conversation, User user, User inviter, Instant createdAt) {
        this.conversation = conversation;
        this.user = user;
        this.inviter = inviter;
        this.createdAt = createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ConversationRequestId implements Serializable {
        private UUID conversation;
        private UUID user;

        public ConversationRequestId(UUID conversation, UUID user) {
            this.conversation = conversation;
            this.user = user;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConversationRequestId that = (ConversationRequestId) o;
            return Objects.equals(conversation, that.conversation) &&
                    Objects.equals(user, that.user);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversation, user);
        }
    }
}
