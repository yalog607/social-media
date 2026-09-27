package com.aloute.comment;

import com.aloute.post.PostView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một bình luận để hiển thị. {@code deleted} là bình luận gốc đã bị xóa nhưng còn trả lời chưa xóa: hiển thị
 * chỗ trống "Bình luận đã bị xóa" để giữ nguyên luồng trả lời (xem {@link CommentService#list}).
 */
public record CommentView(
        UUID id,
        PostView.AuthorView author,
        String contentHtml,
        Instant createdAt,
        boolean mine,
        boolean deleted,
        List<CommentView> replies) {

    public boolean hasReplies() {
        return !replies.isEmpty();
    }
}
