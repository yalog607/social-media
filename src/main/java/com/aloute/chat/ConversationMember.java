package com.aloute.chat;

import com.aloute.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Một người trong một cuộc trò chuyện. {@code lastReadAt} dùng để tính số tin chưa đọc. */
@Entity
@Table(name = "conversation_members", uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ConversationMember {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** Do {@link ChatService} gán bằng {@code clock.instant()}, cùng nguồn thời gian với {@code lastReadAt}. */
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    /** Vai trò trong nhóm; hội thoại 1-1 luôn là {@code MEMBER}. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private GroupRole role = GroupRole.MEMBER;

    /** Biệt danh do người khác (hoặc chính mình) đặt trong hội thoại này; null = dùng tên thật. */
    @Column(length = 40)
    private String nickname;
}
