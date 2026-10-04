package com.aloute.comment;

import com.aloute.common.RateAction;
import com.aloute.common.RateLimiter;
import com.aloute.media.MediaService;
import com.aloute.mention.MentionService;
import com.aloute.notification.NotificationService;
import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.post.PostTextRenderer;
import com.aloute.post.PostView;
import com.aloute.user.Profile;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import com.aloute.wallet.FanBadge;
import com.aloute.wallet.FanService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thêm, xóa và liệt kê bình luận. Trả lời chỉ có MỘT cấp: trả lời của một trả lời được tự động gắn về
 * bình luận gốc của nó (xem {@link #create}).
 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final PostService posts;
    private final UserRepository users;
    private final RateLimiter rateLimiter;
    private final NotificationService notifications;
    private final Clock clock;
    private final FanService fans;
    private final MentionService mentions;
    private final MediaService media;

    public CommentService(CommentRepository comments, PostService posts, UserRepository users,
                          RateLimiter rateLimiter, NotificationService notifications, Clock clock,
                          FanService fans, MentionService mentions, MediaService media) {
        this.media = media;
        this.fans = fans;
        this.mentions = mentions;
        this.comments = comments;
        this.posts = posts;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * @param parentId bình luận đang trả lời, hoặc null nếu là bình luận gốc mới
     * @throws com.aloute.post.PostNotFoundException bài không tồn tại hoặc không xem được
     * @throws InvalidCommentException               nội dung rỗng/quá dài, hoặc {@code parentId} không thuộc bài này
     */
    @Transactional
    public Comment create(UUID authorId, UUID postId, UUID parentId, String content) {
        return create(authorId, postId, parentId, content, null);
    }

    /**
     * Như {@link #create(UUID, UUID, UUID, String)}, thêm một ảnh đính kèm (không bắt buộc). Bình luận hợp lệ khi có chữ
     * HOẶC ảnh. Ảnh được lưu sau khi mọi kiểm tra khác đã qua, và bị xóa khỏi đĩa nếu giao dịch không commit.
     *
     * @throws com.aloute.media.InvalidMediaException ảnh không hợp lệ hoặc quá lớn
     */
    @Transactional
    public Comment create(UUID authorId, UUID postId, UUID parentId, String content, MultipartFile image) {
        rateLimiter.check(RateAction.COMMENT, authorId);
        Post post = posts.getVisible(postId, authorId);
        boolean hasImage = image != null && !image.isEmpty();
        String text = clean(content, hasImage);

        Comment parent = null;
        if (parentId != null) {
            Comment target = comments.findWithAuthors(parentId)
                    .filter(c -> !c.isDeleted() && c.getPost().getId().equals(postId))
                    .orElseThrow(() -> new InvalidCommentException("Bình luận bạn đang trả lời không còn nữa."));
            // Rút gọn về một cấp: trả lời của một trả lời gắn thẳng về bình luận gốc
            parent = target.isReply() ? target.getParent() : target;
        }

        MediaService.StoredMedia stored = media.storeCommentImage(image);
        discardImageUnlessCommitted(stored);

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setImageUrl(stored == null ? null : stored.url());
        comment.setAuthor(users.getReferenceById(authorId));
        comment.setParent(parent);
        comment.setContent(text);
        Comment saved = comments.save(comment);
        java.util.Set<UUID> notified = new java.util.HashSet<>();
        if (parent == null) {
            notifications.postCommented(authorId, post);
            notified.add(post.getAuthor().getId());
        } else {
            notifications.commentReplied(authorId, parent.getAuthor().getId(), post);
            notified.add(parent.getAuthor().getId());
        }
        mentions.notifyComment(authorId, post, text, notified);
        return saved;
    }

    /** Chỉ tác giả bình luận hoặc tác giả bài viết xóa được. Xóa mềm: giữ chỗ nếu còn trả lời chưa xóa. */
    @Transactional
    public void delete(UUID actorId, UUID commentId) {
        Comment comment = comments.findWithAuthors(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(CommentNotFoundException::new);
        boolean allowed = comment.getAuthor().getId().equals(actorId) || comment.getPost().getAuthor().getId().equals(actorId);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không thể xóa bình luận này.");
        }
        comment.setDeletedAt(clock.instant());
    }

    /** @throws com.aloute.post.PostNotFoundException bài không tồn tại hoặc không xem được */
    @Transactional(readOnly = true)
    public List<CommentView> list(UUID postId, UUID viewerId) {
        Post post = posts.getVisible(postId, viewerId);
        List<Comment> flat = comments.findByPostId(postId);
        Map<UUID, FanBadge> badges = fans.badgesFor(post.getAuthor().getId(),
                flat.stream().map(c -> c.getAuthor().getId()).distinct().toList());

        Map<UUID, List<Comment>> repliesByParent = new LinkedHashMap<>();
        List<Comment> roots = new ArrayList<>();
        for (Comment comment : flat) {
            if (comment.isReply()) {
                repliesByParent.computeIfAbsent(comment.getParent().getId(), id -> new ArrayList<>()).add(comment);
            } else {
                roots.add(comment);
            }
        }

        List<CommentView> views = new ArrayList<>();
        for (Comment root : roots) {
            List<Comment> replies = repliesByParent.getOrDefault(root.getId(), List.of());
            if (root.isDeleted() && replies.isEmpty()) {
                continue; // không còn gì để hiện, kể cả chỗ trống
            }
            List<CommentView> replyViews = replies.stream()
                    .filter(reply -> !reply.isDeleted()) // trả lời là lá: xóa thì biến mất hẳn, không cần chỗ trống
                    .map(reply -> toView(reply, viewerId, List.of(), badges))
                    .toList();
            views.add(toView(root, viewerId, replyViews, badges));
        }
        return views;
    }

    private static CommentView toView(Comment comment, UUID viewerId, List<CommentView> replies,
                                      Map<UUID, FanBadge> badges) {
        User author = comment.getAuthor();
        Profile profile = author.getProfile();
        boolean mine = viewerId != null && viewerId.equals(author.getId());
        boolean deleted = comment.isDeleted();
        return new CommentView(
                comment.getId(),
                new PostView.AuthorView(author.getId(), author.getUsername(), profile.getDisplayName(),
                        profile.getAvatarUrl(), author.primaryRole()),
                deleted ? "" : PostTextRenderer.toSafeHtml(comment.getContent()),
                deleted ? null : comment.getImageUrl(),
                comment.getCreatedAt(),
                mine,
                deleted,
                replies,
                badges.get(author.getId()));
    }

    /** Nếu giao dịch không commit (rollback hoặc commit lỗi) thì xóa file ảnh đã ghi ra đĩa. */
    private void discardImageUnlessCommitted(MediaService.StoredMedia stored) {
        if (stored == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    media.discard(List.of(stored));
                }
            }
        });
    }

    /** Bỏ ký tự điều khiển, cắt khoảng trắng hai đầu, kiểm tra độ dài; rỗng chỉ được phép khi có ảnh kèm theo. */
    private static String clean(String content, boolean hasImage) {
        String text = content == null ? "" : content.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (text.isEmpty() && !hasImage) {
            throw new InvalidCommentException("Bình luận không được để trống.");
        }
        if (text.codePointCount(0, text.length()) > Comment.MAX_CONTENT_LENGTH) {
            throw new InvalidCommentException("Bình luận tối đa " + Comment.MAX_CONTENT_LENGTH + " ký tự.");
        }
        return text;
    }
}
