package com.aloute.post;

import com.aloute.media.MediaKind;
import com.aloute.user.Role;
import com.aloute.user.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu một bài để hiển thị. Chỉ chứa giá trị đơn giản, không có thực thể JPA, nên template không bao giờ
 * gây truy vấn ngầm. {@code contentHtml} đã được escape an toàn (dùng với {@code th:utext}).
 * {@code rawContent} là chữ gốc để điền vào ô sửa bài; chỉ có giá trị với chủ bài, người khác nhận {@code null}.
 */
public record PostView(
        UUID id,
        AuthorView author,
        String contentHtml,
        Visibility visibility,
        Instant createdAt,
        boolean edited,
        List<MediaView> media,
        boolean mine,
        String rawContent) {

    public record AuthorView(UUID id, String username, String displayName, String avatarUrl, Role primaryRole) {
    }

    public record MediaView(MediaKind kind, String url, String contentType) {

        public boolean isVideo() {
            return kind == MediaKind.VIDEO;
        }
    }

    public boolean hasMedia() {
        return !media.isEmpty();
    }

    public boolean hasVideo() {
        return media.stream().anyMatch(MediaView::isVideo);
    }

    /** Có nội dung chữ hay không (bài chỉ có ảnh/video thì không). */
    public boolean hasText() {
        return contentHtml != null && !contentHtml.isEmpty();
    }

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }
}
