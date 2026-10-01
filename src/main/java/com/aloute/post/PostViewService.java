package com.aloute.post;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Đếm lượt xem bài cho Insights của Creator. Chỉ tính người đã đăng nhập, tối đa MỘT lượt cho mỗi (người, bài, ngày UTC)
 * nhờ khóa chính của bảng, và không tính chính tác giả — nên bấm F5 liên tục không làm số liệu phình lên.
 */
@Service
public class PostViewService {

    private final JdbcTemplate jdbc;
    private final PostService posts;
    private final Clock clock;

    public PostViewService(JdbcTemplate jdbc, PostService posts, Clock clock) {
        this.jdbc = jdbc;
        this.posts = posts;
        this.clock = clock;
    }

    /**
     * @return true nếu vừa ghi nhận một lượt xem mới
     * @throws PostNotFoundException bài không tồn tại hoặc {@code viewerId} không được xem
     */
    @Transactional
    public boolean record(UUID viewerId, UUID postId) {
        Post post = posts.getVisible(postId, viewerId);
        if (post.getAuthor().getId().equals(viewerId) || post.isShare()) {
            return false;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return jdbc.update("insert into post_views (post_id, viewer_id, day) values (?, ?, ?) on conflict do nothing",
                postId, viewerId, Date.valueOf(today)) == 1;
    }
}
