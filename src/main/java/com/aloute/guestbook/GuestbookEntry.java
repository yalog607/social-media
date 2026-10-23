package com.aloute.guestbook;

import com.aloute.user.User;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Mẩu lưu bút dán trên trang cá nhân của bạn bè. */
@Entity
@Table(name = "guestbook_entries")
@Getter
@Setter
@NoArgsConstructor
public class GuestbookEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_user_id", nullable = false, updatable = false)
    private User profileUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "color_theme", nullable = false, length = 30)
    private String colorTheme = "lemon";

    @Column(name = "sticker_icon", length = 50)
    private String stickerIcon = "🌸";

    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false;

    @Column(name = "is_hidden", nullable = false)
    private boolean isHidden = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GuestbookEntry(User profileUser, User author, String content, String colorTheme, String stickerIcon) {
        this.profileUser = profileUser;
        this.author = author;
        this.content = content;
        this.colorTheme = (colorTheme != null && !colorTheme.isBlank()) ? colorTheme : "lemon";
        this.stickerIcon = (stickerIcon != null && !stickerIcon.isBlank()) ? stickerIcon : "🌸";
        this.isPinned = false;
        this.isHidden = false;
    }
}
