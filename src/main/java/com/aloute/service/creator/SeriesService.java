package com.aloute.service.creator;

import com.aloute.dto.creator.SeriesView;
import com.aloute.exception.creator.InvalidSeriesException;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.model.post.Post;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.post.PostRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.post.PostService;
import com.aloute.service.social.BlockService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Series (chuỗi bài viết) của Creator: gom các bài thường của chính Creator thành một chuỗi có trang riêng. Bài trong
 * series xếp theo thời gian đăng. Quyền xem từng bài vẫn theo {@link PostService#canView}; xóa series là xóa mềm và
 * không xóa bài. Chỉ bài thường, đã đăng, cá nhân (không phải chia sẻ/nhóm/ẩn danh/poll/hỏi đáp) mới thêm được.
 */
@Service
public class SeriesService {

    public static final int MAX_TITLE = 80;
    public static final int MAX_DESCRIPTION = 300;
    public static final int MAX_SERIES_PER_CREATOR = 20;
    public static final int MAX_POSTS_PER_SERIES = 100;
    private static final int CANDIDATE_LIMIT = 50;

    private static final String VIEW_SQL = """
            select s.id, s.title, s.description, s.creator_id, u.username, p.display_name, p.avatar_url,
                   (select count(*) from posts x where x.series_id = s.id and x.deleted_at is null and x.scheduled_at is null),
                   s.created_at
            from series s join users u on u.id = s.creator_id join profiles p on p.user_id = u.id
            """;
    private static final RowMapper<SeriesView> VIEW_MAPPER = (rs, i) -> new SeriesView(
            rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class),
            rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getTimestamp(9).toInstant());

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PostRepository postRepository;
    private final PostService posts;
    private final BlockService blocks;
    private final Clock clock;

    public SeriesService(JdbcTemplate jdbc, UserRepository users, PostRepository postRepository, PostService posts,
                         BlockService blocks, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.postRepository = postRepository;
        this.posts = posts;
        this.blocks = blocks;
        this.clock = clock;
    }

    /** @return id series mới @throws InvalidSeriesException không phải Creator, nội dung sai hoặc quá số series cho phép */
    @Transactional
    public UUID create(UUID creatorId, String title, String description) {
        User creator = users.findById(creatorId).filter(User::isActive).filter(u -> u.hasRole(Role.CREATOR))
                .orElseThrow(() -> new InvalidSeriesException("Chỉ Creator mới tạo được series."));
        String cleanTitle = clean(title, MAX_TITLE, "Tiêu đề", true);
        String cleanDescription = clean(description, MAX_DESCRIPTION, "Mô tả", false);
        jdbc.query("select id from users where id = ? for update", rs -> { }, creator.getId());
        Long count = jdbc.queryForObject("select count(*) from series where creator_id = ? and deleted_at is null",
                Long.class, creatorId);
        if (count != null && count >= MAX_SERIES_PER_CREATOR) {
            throw new InvalidSeriesException("Bạn chỉ tạo được tối đa " + MAX_SERIES_PER_CREATOR + " series.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into series (id, creator_id, title, description, created_at) values (?, ?, ?, ?, ?)",
                id, creatorId, cleanTitle, cleanDescription, Timestamp.from(clock.instant()));
        return id;
    }

    @Transactional
    public void update(UUID creatorId, UUID seriesId, String title, String description) {
        owned(creatorId, seriesId);
        jdbc.update("update series set title = ?, description = ? where id = ?",
                clean(title, MAX_TITLE, "Tiêu đề", true), clean(description, MAX_DESCRIPTION, "Mô tả", false), seriesId);
    }

    /** Xóa mềm series và gỡ các bài ra khỏi series (bài vẫn còn). */
    @Transactional
    public void delete(UUID creatorId, UUID seriesId) {
        owned(creatorId, seriesId);
        jdbc.update("update series set deleted_at = ? where id = ?", Timestamp.from(clock.instant()), seriesId);
        jdbc.update("update posts set series_id = null where series_id = ?", seriesId);
    }

    /** Thêm một bài của chính Creator vào series (chuyển từ series khác nếu bài đang ở series khác). */
    @Transactional
    public void addPost(UUID creatorId, UUID seriesId, UUID postId) {
        owned(creatorId, seriesId);
        // Khóa dòng series để hai lần thêm đồng thời không cùng vượt giới hạn
        jdbc.query("select id from series where id = ? for update", rs -> { }, seriesId);
        Long inSeries = jdbc.queryForObject(
                "select count(*) from posts where series_id = ? and deleted_at is null and scheduled_at is null",
                Long.class, seriesId);
        if (inSeries != null && inSeries >= MAX_POSTS_PER_SERIES) {
            throw new InvalidSeriesException("Mỗi series tối đa " + MAX_POSTS_PER_SERIES + " bài.");
        }
        int updated = jdbc.update("""
                update posts set series_id = ?
                where id = ? and author_id = ? and deleted_at is null and scheduled_at is null and post_kind = 'POST'
                  and shared_post_id is null and group_id is null and is_anonymous = false""", seriesId, postId, creatorId);
        if (updated == 0) {
            throw new InvalidSeriesException("Bài này không thêm vào series được (chỉ bài thường đã đăng của bạn).");
        }
    }

    @Transactional
    public void removePost(UUID creatorId, UUID seriesId, UUID postId) {
        owned(creatorId, seriesId);
        jdbc.update("update posts set series_id = null where id = ? and series_id = ? and author_id = ?",
                postId, seriesId, creatorId);
    }

    @Transactional(readOnly = true)
    public List<SeriesView> mine(UUID creatorId) {
        return jdbc.query(VIEW_SQL + " where s.creator_id = ? and s.deleted_at is null order by s.created_at desc, s.id",
                VIEW_MAPPER, creatorId);
    }

    /** Series của một Creator để hiển thị trên trang hồ sơ công khai; rỗng nếu người xem bị chặn với Creator. */
    @Transactional(readOnly = true)
    public List<SeriesView> forProfile(UUID creatorId, UUID viewerId) {
        if (viewerId != null && blocks.isBlockedEitherWay(creatorId, viewerId)) {
            return List.of();
        }
        return mine(creatorId);
    }

    /** @throws PostNotFoundException series không có, đã xóa hoặc không phải của {@code creatorId} */
    @Transactional(readOnly = true)
    public SeriesView owned(UUID creatorId, UUID seriesId) {
        return jdbc.query(VIEW_SQL + " where s.id = ? and s.creator_id = ? and s.deleted_at is null", VIEW_MAPPER,
                seriesId, creatorId).stream().findFirst().orElseThrow(PostNotFoundException::new);
    }

    /** Các bài trong series (rút gọn) cho trang quản lý, cũ nhất trước. */
    @Transactional(readOnly = true)
    public List<SeriesView.PostChoice> members(UUID creatorId, UUID seriesId) {
        owned(creatorId, seriesId);
        return jdbc.query("""
                select id, left(content, 80), created_at from posts
                where series_id = ? and deleted_at is null and scheduled_at is null order by created_at, id""",
                choiceMapper(), seriesId);
    }

    /** Bài của Creator có thể thêm vào series này (chưa nằm trong series này), mới nhất trước. */
    @Transactional(readOnly = true)
    public List<SeriesView.PostChoice> candidates(UUID creatorId, UUID seriesId) {
        owned(creatorId, seriesId);
        return jdbc.query("""
                select id, left(content, 80), created_at from posts
                where author_id = ? and deleted_at is null and scheduled_at is null and post_kind = 'POST'
                  and shared_post_id is null and group_id is null and is_anonymous = false
                  and (series_id is null or series_id <> ?)
                order by created_at desc, id limit\s""" + CANDIDATE_LIMIT, choiceMapper(), creatorId, seriesId);
    }

    /** Series để xem công khai. */
    public record Page(SeriesView series, List<Post> posts) {
    }

    /**
     * @param viewerId người xem (null = khách)
     * @throws PostNotFoundException series không có/đã xóa, Creator không còn hoạt động, hoặc người xem bị chặn với Creator
     */
    @Transactional(readOnly = true)
    public Page page(UUID seriesId, UUID viewerId) {
        SeriesView series = jdbc.query(VIEW_SQL + " where s.id = ? and s.deleted_at is null", VIEW_MAPPER, seriesId)
                .stream().findFirst().orElseThrow(PostNotFoundException::new);
        boolean active = users.findById(series.creatorId()).filter(User::isActive).isPresent();
        if (!active || (viewerId != null && blocks.isBlockedEitherWay(viewerId, series.creatorId()))) {
            throw new PostNotFoundException();
        }
        List<UUID> ids = jdbc.queryForList("select id from posts where series_id = ? and deleted_at is null and scheduled_at is null order by created_at, id limit "
                + MAX_POSTS_PER_SERIES, UUID.class, seriesId);
        List<Post> visible = ids.isEmpty() ? List.of() : postRepository.findLiveByIds(ids).stream()
                .filter(p -> posts.canView(p, viewerId))
                .sorted(Comparator.comparing(Post::getCreatedAt).thenComparing(Post::getId))
                .toList();
        return new Page(series, visible);
    }

    /** Tiêu đề series (chưa xóa) theo lô, khóa là id series. */
    @Transactional(readOnly = true)
    public Map<UUID, String> titlesFor(Collection<UUID> seriesIds) {
        Map<UUID, String> titles = new HashMap<>();
        if (seriesIds.isEmpty()) {
            return titles;
        }
        String in = String.join(",", seriesIds.stream().map(i -> "?").toList());
        jdbc.query("select id, title from series where deleted_at is null and id in (" + in + ")",
                rs -> { titles.put(rs.getObject(1, UUID.class), rs.getString(2)); }, seriesIds.toArray());
        return titles;
    }

    private static RowMapper<SeriesView.PostChoice> choiceMapper() {
        return (rs, i) -> {
            String snippet = rs.getString(2);
            return new SeriesView.PostChoice(rs.getObject(1, UUID.class),
                    snippet == null || snippet.isBlank() ? "(Bài chỉ có ảnh/video)" : snippet, rs.getTimestamp(3).toInstant());
        };
    }

    private static String clean(String text, int max, String field, boolean required) {
        String cleaned = text == null ? "" : text.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (required && cleaned.isEmpty()) {
            throw new InvalidSeriesException(field + " không được để trống.");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > max) {
            throw new InvalidSeriesException(field + " tối đa " + max + " ký tự.");
        }
        return cleaned;
    }
}
