package com.aloute.service.creator;

import com.aloute.dto.creator.InsightsView;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thống kê cho Creator. Chỉ tính bài của chính {@code creatorId} còn sống (chưa xóa mềm); bình luận đã xóa không
 * được đếm. Mỗi số liệu là một truy vấn gom theo ngày, nên chi phí không phụ thuộc số bài.
 */
@Service
public class InsightsService {

    public static final int MAX_DAYS = 90;
    private static final int TOP_POSTS = 5;
    private static final int SNIPPET_LENGTH = 80;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InsightsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @param days số ngày gần nhất, kẹp vào khoảng 1–{@value #MAX_DAYS}; ngày hôm nay là ngày cuối */
    @Transactional(readOnly = true)
    public InsightsView insights(UUID creatorId, int days) {
        int range = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate first = today.minusDays(range - 1L);
        java.sql.Timestamp from = java.sql.Timestamp.from(first.atStartOfDay(ZoneOffset.UTC).toInstant());

        Map<LocalDate, Long> reactions = perDay("""
                select (r.created_at at time zone 'UTC')::date d, count(*) from reactions r
                join posts p on p.id = r.post_id
                where p.author_id = ? and p.deleted_at is null and r.created_at >= ? group by d""", creatorId, from);
        Map<LocalDate, Long> comments = perDay("""
                select (c.created_at at time zone 'UTC')::date d, count(*) from comments c
                join posts p on p.id = c.post_id
                where p.author_id = ? and p.deleted_at is null and c.deleted_at is null and c.created_at >= ? group by d""",
                creatorId, from);
        Map<LocalDate, Long> shares = perDay("""
                select (s.created_at at time zone 'UTC')::date d, count(*) from posts s
                join posts p on p.id = s.shared_post_id
                where p.author_id = ? and p.deleted_at is null and s.deleted_at is null and s.created_at >= ? group by d""",
                creatorId, from);
        Map<LocalDate, Long> views = perDay("""
                select v.day d, count(*) from post_views v join posts p on p.id = v.post_id
                where p.author_id = ? and p.deleted_at is null and v.day >= ((?)::timestamptz at time zone 'UTC')::date group by d""",
                creatorId, from);
        Map<LocalDate, Long> followers = perDay("""
                select (f.created_at at time zone 'UTC')::date d, count(*) from follows f
                where f.followee_id = ? and f.created_at >= ? group by d""", creatorId, from);

        List<InsightsView.DayPoint> series = new ArrayList<>(range);
        long sumReactions = 0;
        long sumComments = 0;
        long sumShares = 0;
        long sumFollowers = 0;
        long sumViews = 0;
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            long r = reactions.getOrDefault(day, 0L);
            long c = comments.getOrDefault(day, 0L);
            long s = shares.getOrDefault(day, 0L);
            long f = followers.getOrDefault(day, 0L);
            long v = views.getOrDefault(day, 0L);
            series.add(new InsightsView.DayPoint(day, r, c, s, f, v));
            sumReactions += r;
            sumComments += c;
            sumShares += s;
            sumFollowers += f;
            sumViews += v;
        }
        Long totalFollowers = jdbc.queryForObject("select count(*) from follows where followee_id = ?", Long.class, creatorId);
        return new InsightsView(range, totalFollowers == null ? 0 : totalFollowers, sumFollowers,
                sumReactions, sumComments, sumShares, sumViews, series, topPosts(creatorId, from));
    }

    private List<InsightsView.TopPost> topPosts(UUID creatorId, java.sql.Timestamp from) {
        return jdbc.query("""
                select p.id, p.content,
                       (select count(*) from reactions r where r.post_id = p.id and r.created_at >= ?) as reactions,
                       (select count(*) from comments c where c.post_id = p.id and c.deleted_at is null and c.created_at >= ?) as comments,
                       (select count(*) from posts s where s.shared_post_id = p.id and s.deleted_at is null and s.created_at >= ?) as shares,
                       (select count(*) from post_views v where v.post_id = p.id and v.day >= ((?)::timestamptz at time zone 'UTC')::date) as views
                from posts p
                where p.author_id = ? and p.deleted_at is null and p.shared_post_id is null
                order by (select count(*) from reactions r where r.post_id = p.id and r.created_at >= ?)
                       + (select count(*) from comments c where c.post_id = p.id and c.deleted_at is null and c.created_at >= ?)
                       + (select count(*) from posts s where s.shared_post_id = p.id and s.deleted_at is null and s.created_at >= ?) desc,
                       p.created_at desc, p.id desc
                limit ?""",
                (rs, i) -> new InsightsView.TopPost(rs.getObject("id", UUID.class), snippet(rs.getString("content")),
                        rs.getLong("reactions"), rs.getLong("comments"), rs.getLong("shares"), rs.getLong("views")),
                from, from, from, from, creatorId, from, from, from, TOP_POSTS).stream()
                .filter(post -> post.total() > 0).toList();
    }

    private Map<LocalDate, Long> perDay(String sql, UUID creatorId, java.sql.Timestamp from) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        jdbc.query(sql, rs -> {
            Date day = rs.getDate(1);
            byDay.put(day.toLocalDate(), rs.getLong(2));
        }, creatorId, from);
        return byDay;
    }

    private static String snippet(String content) {
        String text = content == null ? "" : content.strip().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            return "(bài chỉ có ảnh/video)";
        }
        return text.codePointCount(0, text.length()) <= SNIPPET_LENGTH
                ? text : text.substring(0, text.offsetByCodePoints(0, SNIPPET_LENGTH)) + "…";
    }
}
