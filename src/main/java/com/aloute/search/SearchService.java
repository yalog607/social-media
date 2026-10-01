package com.aloute.search;

import com.aloute.common.LikeEscape;
import com.aloute.common.TextNormalizer;
import com.aloute.post.Post;
import com.aloute.post.PostRepository;
import com.aloute.post.PostView;
import com.aloute.post.PostViewAssembler;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tìm người dùng, bài viết và hashtag. Chỉ tìm trong bài {@code PUBLIC} và người dùng còn hoạt động.
 * Truy vấn ngắn hơn {@value #MIN_QUERY_LENGTH} hoặc dài hơn {@value #MAX_QUERY_LENGTH} ký tự coi như
 * chưa tìm gì (trả danh sách rỗng), để không phải ném lỗi cho một ô tìm kiếm còn đang gõ dở.
 */
@Service
public class SearchService {

    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_QUERY_LENGTH = 100;
    private static final int RESULT_LIMIT = 20;
    private static final int TRENDING_LIMIT = 8;

    private final PostRepository posts;
    private final UserRepository users;
    private final PostViewAssembler assembler;
    private final JdbcTemplate jdbc;

    public SearchService(PostRepository posts, UserRepository users, PostViewAssembler assembler, JdbcTemplate jdbc) {
        this.posts = posts;
        this.users = users;
        this.assembler = assembler;
        this.jdbc = jdbc;
    }

    public static boolean isValidQuery(String query) {
        return query != null && query.length() >= MIN_QUERY_LENGTH && query.length() <= MAX_QUERY_LENGTH;
    }

    @Transactional(readOnly = true)
    public List<PostView> searchPosts(String query, UUID viewerId) {
        String normalized = TextNormalizer.forSearch(query);
        if (!isValidQuery(normalized)) {
            return List.of();
        }
        List<UUID> ids = posts.searchPublicIds(LikeEscape.escape(normalized), normalized, RESULT_LIMIT);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Post> byId = new HashMap<>();
        for (Post post : posts.findLiveByIds(ids)) {
            byId.put(post.getId(), post);
        }
        List<Post> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return assembler.assemble(ordered, viewerId);
    }

    @Transactional(readOnly = true)
    public List<PostView.AuthorView> searchUsers(String query) {
        String normalized = TextNormalizer.forSearch(query);
        if (!isValidQuery(normalized)) {
            return List.of();
        }
        List<UUID> ids = users.searchActiveIds(LikeEscape.escape(normalized), normalized, RESULT_LIMIT);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> byId = new HashMap<>();
        for (User user : users.findActiveByIds(ids)) {
            byId.put(user.getId(), user);
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull)
                .map(u -> new PostView.AuthorView(u.getId(), u.getUsername(), u.getProfile().getDisplayName(),
                        u.getProfile().getAvatarUrl(), u.primaryRole()))
                .toList();
    }

    /** Hashtag khớp phần đầu (giống gõ dở một thẻ), nhiều bài nhất trước. */
    @Transactional(readOnly = true)
    public List<HashtagResult> searchHashtags(String query) {
        String normalized = TextNormalizer.forSearch(query).replaceFirst("^#", "");
        if (!isValidQuery(normalized)) {
            return List.of();
        }
        return jdbc.query("""
                select tag, count(*) as total from post_hashtags ph join posts p on p.id = ph.post_id
                where p.deleted_at is null and p.visibility = 'PUBLIC' and tag ilike ? || '%' escape '\\'
                group by tag order by count(*) desc, tag asc limit ?""",
                (rs, rowNum) -> new HashtagResult(rs.getString("tag"), rs.getLong("total")),
                LikeEscape.escape(normalized), RESULT_LIMIT);
    }

    /** Hashtag được dùng nhiều nhất trong 7 ngày gần đây, cho khung "đang hot". */
    @Transactional(readOnly = true)
    public List<HashtagResult> trending() {
        return jdbc.query("""
                select tag, count(*) as total from post_hashtags ph join posts p on p.id = ph.post_id
                where p.deleted_at is null and p.visibility = 'PUBLIC' and p.created_at > now() - interval '7 days'
                  and ph.tag not in (select tag from banned_hashtags)
                group by tag order by count(*) desc, tag asc limit ?""",
                (rs, rowNum) -> new HashtagResult(rs.getString("tag"), rs.getLong("total")),
                TRENDING_LIMIT);
    }
}
