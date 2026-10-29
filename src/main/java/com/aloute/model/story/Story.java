package com.aloute.model.story;

import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
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

/** Khoảnh khắc 24H (Story) tự động hết hạn sau 24 giờ. */
@Entity
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
public class Story {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16)
    private StoryMediaType mediaType = StoryMediaType.IMAGE;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "emoji", length = 16)
    private String emoji;

    @Column(name = "background_color", length = 32)
    private String backgroundColor = "lavender";

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private Visibility visibility = Visibility.FRIENDS;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}

