package com.aloute.model.chat;

import com.aloute.model.sticker.Sticker;
import com.aloute.model.user.User;
import com.aloute.service.chat.MessageService;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Một tin nhắn: có chữ, có file đính kèm, nhãn dán, hoặc kết hợp (xem {@link MessageService}). */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    public static final int MAX_CONTENT_LENGTH = 2000;

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @Column(length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false)
    private boolean pinned = false;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;

    @OneToOne(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private MessageAttachment attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sticker_id")
    private Sticker sticker;

    @Column(name = "giphy_id", length = 64)
    private String giphyId;

    /** Do {@link MessageService} gán bằng {@code clock.instant()}, cùng nguồn thời gian với {@code lastReadAt}
     * của {@link ConversationMember} — so hai giờ khác nguồn (giờ hệ thống thật vs. đồng hồ có thể chỉnh trong test)
     * sẽ làm sai lệch việc tính tin chưa đọc. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void setAttachment(MessageAttachment attachment) {
        this.attachment = attachment;
        if (attachment != null) {
            attachment.setMessage(this);
        }
    }
}
