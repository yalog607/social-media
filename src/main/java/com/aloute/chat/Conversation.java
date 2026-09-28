package com.aloute.chat;

import com.aloute.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Một cuộc trò chuyện, 1-1 ({@code DIRECT}) hoặc nhóm ({@code GROUP}). {@code directKey} chỉ có ở loại DIRECT,
 * chuẩn hóa từ cặp id để không bao giờ có hai hội thoại 1-1 khác nhau cho cùng một cặp người ({@link ChatService}).
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    public static final int MAX_TITLE_LENGTH = 100;

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ConversationType type;

    /** Chỉ dùng cho GROUP; DIRECT hiển thị tên người kia nên không cần tiêu đề riêng. */
    @Column(length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "direct_key", length = 80, updatable = false)
    private String directKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    /** Do {@link ChatService} gán bằng {@code clock.instant()} (không dùng {@code @CreationTimestamp}, vốn lấy
     * giờ hệ thống thật) để cùng nguồn thời gian với {@code ConversationMember}/{@code Message} khi so sánh. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isGroup() {
        return type == ConversationType.GROUP;
    }
}
