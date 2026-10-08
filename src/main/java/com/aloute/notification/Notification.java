package com.aloute.notification;

import com.aloute.post.Post;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Một thông báo gửi tới {@code recipient}, do {@code actor} gây ra (thả cảm xúc, bình luận, kết bạn...). */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    /** Bài viết liên quan (cảm xúc/bình luận/chia sẻ trên bài nào); null với kết bạn và theo dõi. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", updatable = false)
    private Post post;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Chỉ có với thông báo nhắn hàng loạt; nội dung nằm ở bản ghi {@code broadcasts}. */
    @Column(name = "broadcast_id")
    private UUID broadcastId;

    @org.hibernate.annotations.Formula("(select b.title from broadcasts b where b.id = broadcast_id)")
    private String broadcastTitle;

    @org.hibernate.annotations.Formula("(select b.body from broadcasts b where b.id = broadcast_id)")
    private String broadcastBody;

    /** Chỉ có với thông báo nhắc tên trong nhóm chat. */
    @Column(name = "conversation_id")
    private UUID conversationId;

    @org.hibernate.annotations.Formula("(select c.title from conversations c where c.id = conversation_id)")
    private String conversationTitle;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "detail")
    private String detail;

    @Column(name = "custom_text")
    private String customText;

    @Column(name = "read_at")
    private Instant readAt;

    public boolean isRead() {
        return readAt != null;
    }
}
