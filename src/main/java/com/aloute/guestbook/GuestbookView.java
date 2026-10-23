package com.aloute.guestbook;

import com.aloute.post.PostView.AuthorView;

import java.time.Instant;
import java.util.UUID;

/** Dữ liệu hiển thị một mẩu lưu bút. */
public record GuestbookView(
        UUID id,
        AuthorView author,
        String content,
        String colorTheme,
        String stickerIcon,
        boolean isPinned,
        boolean isHidden,
        Instant createdAt,
        boolean canPin,
        boolean canHide,
        boolean canDelete
) {
}
