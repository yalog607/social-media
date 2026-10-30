package com.aloute.model.chat;

import com.aloute.service.chat.ChatService;

import com.aloute.model.user.User;
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
import org.hibernate.annotations.SQLRestriction;
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
@SQLRestriction("deleted_at is null")
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

    @Column(nullable = false)
    private boolean approvalRequired = false;

    @Column(nullable = false)
    private boolean allowAnyoneChangeNickname = true;

    @Column(name = "members_can_send_messages", nullable = false)
    private boolean membersCanSendMessages = true;

    /** Ảnh đại diện nhóm (chỉ GROUP); null = dùng ảnh mặc định. */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** Mô tả hoặc giới thiệu ngắn về nhóm/câu lạc bộ. */
    @Column(length = 500)
    private String description;

    /** Nội quy nhóm do ban quản trị nhóm soạn thảo. */
    @Column(length = 2000)
    private String rules;

    /** Câu hỏi duyệt thành viên trước khi vào nhóm (nếu approvalRequired = true). */
    @Column(name = "approval_question", length = 500)
    private String approvalQuestion;

    /** Đánh dấu đây là Hội nhóm (Social Group) hay Nhóm trò chuyện thuần túy (Chat Group). */
    @Column(name = "is_social_group", nullable = false)
    private boolean socialGroup = false;

    /** Phòng chat của nhóm có đang mở/liên kết hay không. Mặc định hội nhóm không liên kết tin nhắn, chỉ khi chủ nhóm mở mới kích hoạt. */
    @Column(name = "chat_enabled", nullable = false)
    private boolean chatEnabled = false;

    @Column(name = "direct_key", length = 80, updatable = false)
    private String directKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    /** Do {@link ChatService} gán bằng {@code clock.instant()} (không dùng {@code @CreationTimestamp}, vốn lấy
     * giờ hệ thống thật) để cùng nguồn thời gian với {@code ConversationMember}/{@code Message} khi so sánh. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isGroup() {
        return type == ConversationType.GROUP;
    }
}
