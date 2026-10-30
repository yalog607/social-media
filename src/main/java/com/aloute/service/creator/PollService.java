package com.aloute.service.creator;

import com.aloute.dto.post.PollView;
import com.aloute.exception.post.InvalidPostException;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.model.notification.NotificationType;
import com.aloute.model.post.Post;
import com.aloute.model.post.PostKind;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.repository.post.PostRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.post.PostService;
import com.aloute.service.social.BlockService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bình chọn của Creator. Poll là một {@link Post} thật ({@link PostKind#POLL}) nên dùng lại bảng tin, quyền xem và bình luận.
 * Bài poll luôn công khai; mỗi người một phiếu cho mỗi poll và không đổi được. Khi tạo, thông báo được chèn cho người
 * theo dõi bằng MỘT câu INSERT ... SELECT (như {@link BroadcastService}); các lượt bầu sau đó không gửi thông báo.
 */
@Service
public class PollService {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 6;
    public static final int MAX_LABEL = 80;
    public static final int MAX_DAYS_OPEN = 30;

    private final JdbcTemplate jdbc;
    private final PostService posts;
    private final PostRepository postRepository;
    private final UserRepository users;
    private final BlockService blocks;
    private final Clock clock;
    private final FollowerNotifier notifier;

    public PollService(JdbcTemplate jdbc, PostService posts, PostRepository postRepository, UserRepository users,
                       BlockService blocks, Clock clock, FollowerNotifier notifier) {
        this.notifier = notifier;
        this.jdbc = jdbc;
        this.posts = posts;
        this.postRepository = postRepository;
        this.users = users;
        this.blocks = blocks;
        this.clock = clock;
    }

    /**
     * @param closesAt giờ đóng bình chọn; null = không giới hạn. Phải ở tương lai và trong {@value #MAX_DAYS_OPEN} ngày.
     * @throws InvalidPostException không phải Creator, câu hỏi/lựa chọn không hợp lệ hoặc giờ đóng sai
     */
    @Transactional
    public Post create(UUID creatorId, String question, List<String> options, Instant closesAt) {
        User creator = users.findById(creatorId).filter(User::isActive).filter(u -> u.hasRole(Role.CREATOR))
                .orElseThrow(() -> new InvalidPostException("Chỉ Creator mới tạo được bình chọn."));
        if (question == null || question.isBlank()) {
            throw new InvalidPostException("Hãy nhập câu hỏi cho bình chọn.");
        }
        List<String> labels = cleanOptions(options);
        Instant now = clock.instant();
        if (closesAt != null && (!closesAt.isAfter(now) || closesAt.isAfter(now.plus(Duration.ofDays(MAX_DAYS_OPEN))))) {
            throw new InvalidPostException("Giờ đóng bình chọn phải trong vòng " + MAX_DAYS_OPEN + " ngày tới.");
        }

        Post post = posts.create(creator.getId(), question, Visibility.PUBLIC, null, null, List.of(), null, null, null, false);
        post.setPostKind(PostKind.POLL);
        post.setPollClosesAt(closesAt);
        for (int i = 0; i < labels.size(); i++) {
            jdbc.update("insert into poll_options (id, post_id, position, label) values (?, ?, ?, ?)",
                    UUID.randomUUID(), post.getId(), (short) i, labels.get(i));
        }
        notifier.notifyFollowers(creatorId, post.getId(), NotificationType.CREATOR_POLL);
        return post;
    }

    /**
     * Bầu một lựa chọn. Mỗi người một phiếu, không đổi được.
     *
     * @throws PostNotFoundException bài không có/không xem được/không phải poll/bị chặn với tác giả/lựa chọn không thuộc poll
     * @throws InvalidPostException  bình chọn đã kết thúc hoặc người này đã bầu
     */
    @Transactional
    public void vote(UUID userId, UUID postId, UUID optionId) {
        Post post = posts.getVisible(postId, userId);
        if (post.getPostKind() != PostKind.POLL || blocks.isBlockedEitherWay(userId, post.getAuthor().getId())) {
            throw new PostNotFoundException();
        }
        Integer belongs = jdbc.queryForObject("select count(*) from poll_options where id = ? and post_id = ?",
                Integer.class, optionId, postId);
        if (belongs == null || belongs == 0) {
            throw new PostNotFoundException();
        }
        if (isClosed(post.getPollClosesAt())) {
            throw new InvalidPostException("Bình chọn này đã kết thúc.");
        }
        int inserted = jdbc.update("""
                insert into poll_votes (post_id, user_id, option_id, created_at) values (?, ?, ?, ?)
                on conflict (post_id, user_id) do nothing""", postId, userId, optionId, Timestamp.from(clock.instant()));
        if (inserted == 0) {
            throw new InvalidPostException("Bạn đã bình chọn rồi.");
        }
    }

    /** Các bài bình chọn của một Creator cho khối "Hỏi đáp & Poll" trên trang hồ sơ (luôn công khai), mới nhất trước. */
    @Transactional(readOnly = true)
    public List<Post> forProfile(UUID creatorId) {
        return postRepository.findByAuthorIdAndPostKindIn(creatorId, List.of(PostKind.POLL));
    }

    /** Dựng {@link PollView} theo LÔ cho các bài poll (khóa là id bài). {@code viewerId} null nếu là khách. */
    @Transactional(readOnly = true)
    public Map<UUID, PollView> viewsFor(Collection<Post> polls, UUID viewerId) {
        Map<UUID, PollView> result = new HashMap<>();
        if (polls.isEmpty()) {
            return result;
        }
        List<UUID> ids = polls.stream().map(Post::getId).toList();
        String in = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] idArgs = ids.toArray();

        Map<UUID, List<Object[]>> optionsByPost = new HashMap<>();
        jdbc.query("select id, post_id, label from poll_options where post_id in (" + in + ") order by post_id, position",
                rs -> {
                    optionsByPost.computeIfAbsent(rs.getObject("post_id", UUID.class), k -> new ArrayList<>())
                            .add(new Object[]{rs.getObject("id", UUID.class), rs.getString("label")});
                }, idArgs);
        Map<UUID, Long> votesByOption = new HashMap<>();
        jdbc.query("select option_id, count(*) c from poll_votes where post_id in (" + in + ") group by option_id",
                rs -> {
                    votesByOption.put(rs.getObject("option_id", UUID.class), rs.getLong("c"));
                }, idArgs);
        Map<UUID, UUID> votedByPost = new HashMap<>();
        if (viewerId != null) {
            List<Object> args = new ArrayList<>(ids);
            args.add(viewerId);
            jdbc.query("select post_id, option_id from poll_votes where post_id in (" + in + ") and user_id = ?",
                    rs -> {
                        votedByPost.put(rs.getObject("post_id", UUID.class), rs.getObject("option_id", UUID.class));
                    }, args.toArray());
        }

        for (Post post : polls) {
            List<Object[]> raw = optionsByPost.getOrDefault(post.getId(), List.of());
            long total = raw.stream().mapToLong(o -> votesByOption.getOrDefault((UUID) o[0], 0L)).sum();
            List<PollView.Option> options = new ArrayList<>(raw.size());
            for (Object[] o : raw) {
                long votes = votesByOption.getOrDefault((UUID) o[0], 0L);
                int percent = total == 0 ? 0 : (int) Math.round(votes * 100.0 / total);
                options.add(new PollView.Option((UUID) o[0], (String) o[1], votes, percent));
            }
            result.put(post.getId(), new PollView(options, total, votedByPost.get(post.getId()),
                    isClosed(post.getPollClosesAt()), post.getPollClosesAt(), viewerId != null));
        }
        return result;
    }

    private boolean isClosed(Instant closesAt) {
        return closesAt != null && !clock.instant().isBefore(closesAt);
    }

    private static List<String> cleanOptions(List<String> options) {
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (options != null) {
            for (String raw : options) {
                String label = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
                if (label.isEmpty()) {
                    continue;
                }
                if (label.codePointCount(0, label.length()) > MAX_LABEL) {
                    throw new InvalidPostException("Mỗi lựa chọn tối đa " + MAX_LABEL + " ký tự.");
                }
                if (!seen.add(label.toLowerCase(Locale.ROOT))) {
                    throw new InvalidPostException("Các lựa chọn không được trùng nhau.");
                }
                labels.add(label);
            }
        }
        if (labels.size() < MIN_OPTIONS || labels.size() > MAX_OPTIONS) {
            throw new InvalidPostException("Bình chọn cần từ " + MIN_OPTIONS + " đến " + MAX_OPTIONS + " lựa chọn.");
        }
        return labels;
    }
}
