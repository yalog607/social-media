package com.aloute.model.group;

import com.aloute.model.chat.Conversation;
import com.aloute.model.post.Post;
import com.aloute.model.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Báo cáo bài viết vi phạm nội quy trong một Hội nhóm.
 */
@Entity
@Table(name = "group_post_reports")
@Getter
@Setter
@NoArgsConstructor
public class GroupPostReport {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Conversation group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupPostReport(Conversation group, Post post, User reporter, String reason, Instant createdAt) {
        this.group = group;
        this.post = post;
        this.reporter = reporter;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
