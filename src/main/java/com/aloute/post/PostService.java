package com.aloute.post;

import com.aloute.common.RateAction;
import com.aloute.common.RateLimiter;
import com.aloute.common.TextNormalizer;
import com.aloute.media.MediaService;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import com.aloute.user.Visibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ bài đăng: tạo, sửa, xóa mềm và kiểm tra quyền xem. Quyền được kiểm tra ở đây (không chỉ ở controller)
 * để mọi nơi gọi đều an toàn. Bài không xem/sửa/xóa được luôn báo {@link PostNotFoundException}.
 */
@Service
public class PostService {

    private final PostRepository posts;
    private final UserRepository users;
    private final MediaService media;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public PostService(PostRepository posts, UserRepository users, MediaService media,
                       RateLimiter rateLimiter, Clock clock) {
        this.posts = posts;
        this.users = users;
        this.media = media;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * @param visibility null thì dùng mức mặc định trong hồ sơ của tác giả
     * @throws InvalidPostException                  bài rỗng, quá dài hoặc tác giả không thể đăng
     * @throws com.aloute.media.InvalidMediaException ảnh/video không hợp lệ
     * @throws com.aloute.common.RateLimitExceededException đăng quá nhanh
     */
    @Transactional
    public Post create(UUID authorId, String content, Visibility visibility,
                       List<MultipartFile> images, MultipartFile video) {
        rateLimiter.check(RateAction.POST, authorId);
        String text = cleanContent(content);
        boolean hasMedia = hasContent(video) || (images != null && images.stream().anyMatch(PostService::hasContent));
        if (text.isEmpty() && !hasMedia) {
            throw new InvalidPostException("Hãy viết gì đó hoặc thêm ảnh/video nhé.");
        }
        User author = users.findById(authorId).filter(User::isActive)
                .orElseThrow(() -> new InvalidPostException("Tài khoản này không thể đăng bài."));

        List<MediaService.StoredMedia> stored = media.storeAll(images, video);
        try {
            Post post = new Post();
            post.setAuthor(author);
            post.setVisibility(visibility != null ? visibility : author.getProfile().getDefaultPostVisibility());
            applyContent(post, text);
            for (MediaService.StoredMedia item : stored) {
                PostMedia entity = new PostMedia();
                entity.setKind(item.kind());
                entity.setUrl(item.url());
                entity.setContentType(item.contentType());
                entity.setSizeBytes(item.sizeBytes());
                post.addMedia(entity);
            }
            Post saved = posts.save(post);
            discardMediaUnlessCommitted(stored);
            return saved;
        } catch (RuntimeException e) {
            media.discard(stored);
            throw e;
        }
    }

    /**
     * Chỉ sửa chữ và quyền xem (media giữ nguyên). Chỉ tác giả sửa được. "Đã chỉnh sửa" chỉ được đánh dấu
     * khi nội dung chữ thật sự đổi.
     */
    @Transactional
    public Post edit(UUID actorId, UUID postId, String content, Visibility visibility) {
        Post post = ownedLivePost(actorId, postId);
        String text = cleanContent(content);
        if (text.isEmpty() && post.getMedia().isEmpty()) {
            throw new InvalidPostException("Bài không có ảnh/video thì cần có chữ nhé.");
        }
        if (!text.equals(post.getContent())) {
            applyContent(post, text);
            post.setEditedAt(clock.instant());
        }
        if (visibility != null) {
            post.setVisibility(visibility);
        }
        return post;
    }

    /** Xóa mềm: bài biến mất khỏi mọi nơi nhưng dữ liệu còn (phục vụ kiểm duyệt ở giai đoạn sau). */
    @Transactional
    public void delete(UUID actorId, UUID postId) {
        ownedLivePost(actorId, postId).setDeletedAt(clock.instant());
    }

    /** @throws PostNotFoundException nếu bài không tồn tại hoặc {@code viewerId} (null = khách) không được xem */
    @Transactional(readOnly = true)
    public Post getVisible(UUID postId, UUID viewerId) {
        Post post = posts.findLive(postId).orElseThrow(PostNotFoundException::new);
        if (!canView(post, viewerId)) {
            throw new PostNotFoundException();
        }
        return post;
    }

    /**
     * Quy tắc xem: tác giả phải còn hoạt động; bài công khai ai cũng xem; còn lại (riêng tư, và tạm thời cả "bạn bè"
     * cho tới khi có tính năng kết bạn) chỉ tác giả xem.
     */
    public static boolean canView(Post post, UUID viewerId) {
        if (post.isDeleted() || !post.getAuthor().isActive()) {
            return false;
        }
        return post.getVisibility() == Visibility.PUBLIC
                || (viewerId != null && viewerId.equals(post.getAuthor().getId()));
    }

    // ---------- Nội bộ ----------

    private Post ownedLivePost(UUID actorId, UUID postId) {
        Post post = posts.findLive(postId).orElseThrow(PostNotFoundException::new);
        if (!post.getAuthor().getId().equals(actorId) || !post.getAuthor().isActive()) {
            throw new PostNotFoundException();
        }
        return post;
    }

    private static void applyContent(Post post, String text) {
        post.setContent(text);
        post.setSearchText(TextNormalizer.forSearch(text));
        post.getHashtags().clear();
        post.getHashtags().addAll(Hashtags.extract(text));
    }

    /** Bỏ ký tự điều khiển (kể cả NUL mà PostgreSQL không nhận), cắt khoảng trắng hai đầu, kiểm tra độ dài. */
    private static String cleanContent(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (text.codePointCount(0, text.length()) > Post.MAX_CONTENT_LENGTH) {
            throw new InvalidPostException("Bài viết tối đa " + Post.MAX_CONTENT_LENGTH + " ký tự.");
        }
        return text;
    }

    private static boolean hasContent(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    /** Nếu giao dịch không commit (rollback hoặc commit lỗi) thì xóa file media đã ghi ra đĩa. */
    private void discardMediaUnlessCommitted(List<MediaService.StoredMedia> stored) {
        if (stored.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    media.discard(stored);
                }
            }
        });
    }
}
