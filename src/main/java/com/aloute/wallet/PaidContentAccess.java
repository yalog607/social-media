package com.aloute.wallet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Ai đã mở khóa bài trả phí nào. Tách riêng khỏi {@link WalletService} để dựng PostView không cần kéo theo cả ví. */
@Component
public class PaidContentAccess {

    private final JdbcTemplate jdbc;

    public PaidContentAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Trong {@code postIds}, những bài mà {@code userId} đã mở khóa (một truy vấn cho cả lô). */
    public Set<UUID> unlockedAmong(UUID userId, Collection<UUID> postIds) {
        Set<UUID> result = new HashSet<>();
        if (userId == null || postIds.isEmpty()) {
            return result;
        }
        String marks = String.join(",", java.util.Collections.nCopies(postIds.size(), "?"));
        Object[] args = new Object[postIds.size() + 1];
        args[0] = userId;
        int i = 1;
        for (UUID id : postIds) {
            args[i++] = id;
        }
        jdbc.query("select post_id from paid_content_unlocks where user_id = ? and post_id in (" + marks + ")",
                rs -> {
                    result.add(rs.getObject(1, UUID.class));
                }, args);
        return result;
    }

    /** @return true nếu vừa ghi nhận mở khóa mới, false nếu đã mở từ trước */
    boolean record(UUID postId, UUID userId, int price) {
        return jdbc.update("""
                insert into paid_content_unlocks (id, post_id, user_id, price) values (?, ?, ?, ?)
                on conflict (post_id, user_id) do nothing""", UUID.randomUUID(), postId, userId, price) == 1;
    }
}
