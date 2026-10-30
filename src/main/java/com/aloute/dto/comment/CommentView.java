package com.aloute.dto.comment;

import com.aloute.dto.post.PostView;
import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.service.comment.CommentService;
import com.aloute.dto.sticker.StickerView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một bình luận để hiển thị. {@code deleted} là bình luận gốc đã bị xóa nhưng còn trả lời chưa xóa: hiển thị
 * chỗ trống "Bình luận đã bị xóa" để giữ nguyên luồng trả lời (xem {@link CommentService#list}).
 * {@code imageUrl} là ảnh đính kèm (null nếu không có hoặc bình luận đã xóa).
 * {@code reactions} là tổng kết cảm xúc của bình luận.
 */
public record CommentView(
        UUID id,
        PostView.AuthorView author,
        String contentHtml,
        String imageUrl,
        Instant createdAt,
        boolean mine,
        boolean deleted,
        List<CommentView> replies,
        ReactionSummary reactions,
        StickerView sticker) {

    public boolean hasReplies() {
        return !replies.isEmpty();
    }
}
