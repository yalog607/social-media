package com.aloute.service.post;

import com.aloute.exception.post.InvalidPostException;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.model.post.Post;
import com.aloute.model.post.PostMedia;
import com.aloute.model.post.PostTag;
import com.aloute.repository.post.PostRepository;
import com.aloute.repository.post.PostTagRepository;
import com.aloute.util.post.Hashtags;

import com.aloute.service.category.CategoryService;
import com.aloute.exception.category.InvalidCategoryException;
import com.aloute.model.common.RateAction;
import com.aloute.service.common.RateLimiter;
import com.aloute.util.common.TextNormalizer;
import com.aloute.service.media.MediaService;
import com.aloute.service.moderation.BannedHashtags;
import com.aloute.service.notification.NotificationService;
import com.aloute.service.social.FriendService;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.model.user.Visibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
    public static final int MAX_TAGS = 10;
    public static final int MAX_UNLOCK_PRICE = 1000;
    public static final int MAX_SCHEDULE_DAYS = 30;

    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final FriendService friends;
    private final NotificationService notifications;
    private final PostTagRepository tags;
    private final BannedHashtags bannedHashtags;
    private final CategoryService categories;

    public PostService(PostRepository posts, UserRepository users, MediaService media,
                       RateLimiter rateLimiter, Clock clock, FriendService friends, NotificationService notifications,
                       PostTagRepository tags, BannedHashtags bannedHashtags, CategoryService categories) {
        this.categories = categories;
        this.bannedHashtags = bannedHashtags;
        this.posts = posts;
        this.users = users;
        this.media = media;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.friends = friends;
        this.notifications = notifications;
        this.tags = tags;
    }

    /**
     * @param visibility null thì dùng mức mặc định trong hồ sơ của tác giả
     * @throws InvalidPostException                  bài rỗng, quá dài hoặc tác giả không thể đăng
     * @throws com.aloute.exception.media.InvalidMediaException ảnh/video không hợp lệ
     * @throws com.aloute.exception.common.RateLimitExceededException đăng quá nhanh
     */
    @Transactional
    public Post create(UUID authorId, String content, Visibility visibility,
                       List<MultipartFile> images, MultipartFile video) {
        return create(authorId, content, visibility, images, video, List.of(), null);
    }

    /**
     * Như {@link #create(UUID, String, Visibility, List, MultipartFile)} và gắn thẻ thêm {@code taggedUserIds}.
     *
     * @throws InvalidPostException thẻ không hợp lệ: quá {@value #MAX_TAGS} người, không phải bạn bè, hoặc bài "Chỉ mình tôi"
     */
    @Transactional
    public Post create(UUID authorId, String content, Visibility visibility,
                       List<MultipartFile> images, MultipartFile video, List<UUID> taggedUserIds) {
        return create(authorId, content, visibility, images, video, taggedUserIds, null, null);
    }

    /**
     * Như trên, thêm {@code unlockPrice}: giá (Xu) để xem bài. Chỉ Creator đặt được, từ 1 đến
     * {@value #MAX_UNLOCK_PRICE}; null là bài miễn phí.
     *
     * @throws InvalidPostException giá không hợp lệ hoặc người đăng chưa phải Creator
     */
    @Transactional
    public Post create(UUID authorId, String content, Visibility visibility,
                       List<MultipartFile> images, MultipartFile video, List<UUID> taggedUserIds, Integer unlockPrice) {
        return create(authorId, content, visibility, images, video, taggedUserIds, unlockPrice, null);
    }

    /**
     * Như trên, thêm {@code scheduledAt}: giờ hẹn đăng. Bài hẹn giờ chỉ chủ bài thấy cho tới khi đến giờ; thông báo
     * gắn thẻ cũng hoãn tới lúc đó. Chỉ Creator hẹn giờ được, trong khoảng từ bây giờ tới {@value #MAX_SCHEDULE_DAYS} ngày.
     *
     * @throws InvalidPostException giờ hẹn không hợp lệ hoặc người đăng chưa phải Creator
     */
    @Transactional
    public Post create(UUID authorId, String content, Visibility visibility, List<MultipartFile> images,
                       MultipartFile video, List<UUID> taggedUserIds, Integer unlockPrice, Instant scheduledAt) {
        return create(authorId, content, visibility, images, video, taggedUserIds, unlockPrice, scheduledAt, null);
    }

    /**
     * Như trên, thêm {@code categoryId}: danh mục (không bắt buộc) do Manager quản lý.
     *
     * @throws InvalidPostException danh mục không tồn tại hoặc đã ngưng dùng
     */
    @Transactional
    public Post create(UUID authorId, String content, Visibility visibility, List<MultipartFile> images,
                       MultipartFile video, List<UUID> taggedUserIds, Integer unlockPrice, Instant scheduledAt,
                       UUID categoryId) {
        rateLimiter.check(RateAction.POST, authorId);
        String text = cleanContent(content);
        rejectBannedHashtags(text);
        boolean hasMedia = hasContent(video) || (images != null && images.stream().anyMatch(PostService::hasContent));
        if (text.isEmpty() && !hasMedia) {
            throw new InvalidPostException("Hãy viết gì đó hoặc thêm ảnh/video nhé.");
        }
        User author = users.findById(authorId).filter(User::isActive)
                .orElseThrow(() -> new InvalidPostException("Tài khoản này không thể đăng bài."));

        if (unlockPrice != null) {
            if (!author.hasRole(com.aloute.model.user.Role.CREATOR)) {
                throw new InvalidPostException("Chỉ Creator mới đặt giá cho bài viết được.");
            }
            if (unlockPrice < 1 || unlockPrice > MAX_UNLOCK_PRICE) {
                throw new InvalidPostException("Giá mở khóa từ 1 đến " + MAX_UNLOCK_PRICE + " Xu.");
            }
        }
        validateSchedule(author, scheduledAt);
        requireUsableCategory(categoryId);
        Visibility chosen = visibility != null ? visibility : author.getProfile().getDefaultPostVisibility();
        List<UUID> tagIds = validTagIds(authorId, taggedUserIds, chosen);

        List<MediaService.StoredMedia> stored = media.storeAll(images, video);
        try {
            Post post = new Post();
            post.setAuthor(author);
            post.setVisibility(chosen);
            post.setUnlockPrice(unlockPrice);
            post.setScheduledAt(scheduledAt);
            post.setCategoryId(categoryId);
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
            for (UUID tagId : tagIds) {
                PostTag tag = new PostTag();
                tag.setPost(saved);
                tag.setTaggedUser(users.getReferenceById(tagId));
                tags.save(tag);
                if (scheduledAt == null) {
                    notifications.postTagged(authorId, tagId, saved);
                }
            }
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
        rejectBannedHashtags(text);
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

    /**
     * Như {@link #edit(UUID, UUID, String, Visibility)} và cập nhật thêm thẻ bạn bè, danh mục.
     *
     * @param taggedUserIds null = giữ nguyên thẻ hiện có; ngược lại là DANH SÁCH THẺ MỚI (thay thế hoàn toàn): người
     *                      mới được gắn nhận thông báo, người bị bỏ thì mất thẻ. Chuyển bài sang "Chỉ mình tôi" thì
     *                      xóa hết thẻ vì người được gắn không còn xem được bài.
     * @param categoryId    danh mục mới, null = bỏ danh mục. Giữ nguyên danh mục cũ (kể cả đã ngưng dùng) cũng được.
     * @throws InvalidPostException thẻ hoặc danh mục không hợp lệ
     */
    @Transactional
    public Post edit(UUID actorId, UUID postId, String content, Visibility visibility,
                     List<UUID> taggedUserIds, UUID categoryId) {
        Post post = ownedLivePost(actorId, postId);
        if (categoryId != null && !categoryId.equals(post.getCategoryId())) {
            requireUsableCategory(categoryId);
        }
        Visibility finalVisibility = visibility != null ? visibility : post.getVisibility();
        // Bài "Chỉ mình tôi" luôn không có thẻ (xem bên dưới), nên không cần kiểm tra danh sách người dùng gửi lên
        List<UUID> wanted = taggedUserIds == null || finalVisibility == Visibility.PRIVATE
                ? null : validTagIds(actorId, taggedUserIds, finalVisibility);
        edit(actorId, postId, content, visibility);
        post.setCategoryId(categoryId);
        if (finalVisibility == Visibility.PRIVATE) {
            wanted = List.of();
        }
        if (wanted != null) {
            syncTags(post, wanted);
        }
        return post;
    }

    /** Đưa danh sách thẻ của bài về đúng {@code wanted}: thêm người mới (kèm thông báo), xóa người bị bỏ. */
    private void syncTags(Post post, List<UUID> wanted) {
        List<PostTag> current = tags.findByPostId(post.getId());
        for (PostTag tag : current) {
            if (!wanted.contains(tag.getTaggedUser().getId())) {
                tags.delete(tag);
            }
        }
        List<UUID> existing = current.stream().map(t -> t.getTaggedUser().getId()).toList();
        for (UUID id : wanted) {
            if (existing.contains(id)) {
                continue;
            }
            PostTag tag = new PostTag();
            tag.setPost(post);
            tag.setTaggedUser(users.getReferenceById(id));
            tags.save(tag);
            if (!post.isScheduled()) {
                notifications.postTagged(post.getAuthor().getId(), id, post);
            }
        }
    }

    private void requireUsableCategory(UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        try {
            categories.requireActive(categoryId);
        } catch (InvalidCategoryException e) {
            throw new InvalidPostException(e.getMessage());
        }
    }

    /**
     * Đăng lại một bài công khai vào trang của người chia sẻ, kèm lời nhắn tùy chọn. Chia sẻ một bài đã là
     * bài chia sẻ thì trỏ thẳng về bài gốc thật sự (không bao giờ trỏ qua một bài chia sẻ khác).
     *
     * @throws PostNotFoundException bài không tồn tại, không công khai, hoặc bài gốc của nó không còn công khai
     * @throws InvalidPostException  lời nhắn quá dài
     */
    @Transactional
    public Post share(UUID actorId, UUID postId, String caption) {
        rateLimiter.check(RateAction.POST, actorId);
        String text = cleanCaption(caption);
        rejectBannedHashtags(text);
        User author = users.findById(actorId).filter(User::isActive)
                .orElseThrow(() -> new InvalidPostException("Tài khoản này không thể đăng bài."));

        Post source = posts.findLive(postId).orElseThrow(PostNotFoundException::new);
        if (source.getVisibility() != Visibility.PUBLIC || source.isScheduled()) {
            throw new PostNotFoundException();
        }
        Post original = source.isShare() ? source.getSharedPost() : source;
        if (original.isDeleted() || original.getVisibility() != Visibility.PUBLIC || !original.getAuthor().isActive()) {
            throw new PostNotFoundException();
        }

        Post share = new Post();
        share.setAuthor(author);
        share.setVisibility(Visibility.PUBLIC);
        share.setSharedPost(original);
        applyContent(share, text);
        Post saved = posts.save(share);
        notifications.postShared(actorId, original);
        return saved;
    }

    /**
     * Đổi giờ hẹn của một bài chưa đăng.
     *
     * @throws PostNotFoundException bài không tồn tại, không phải của {@code actorId}, hoặc không còn là bài hẹn giờ
     */
    @Transactional
    public Post reschedule(UUID actorId, UUID postId, Instant scheduledAt) {
        Post post = ownedLivePost(actorId, postId);
        if (!post.isScheduled()) {
            throw new PostNotFoundException();
        }
        validateSchedule(post.getAuthor(), scheduledAt);
        post.setScheduledAt(scheduledAt);
        return post;
    }

    /** Các bài hẹn giờ chưa đăng của {@code authorId}, sớm nhất trước. */
    @Transactional(readOnly = true)
    public List<Post> scheduledOf(UUID authorId) {
        return posts.findScheduledByAuthor(authorId);
    }

    /** Xóa mềm: bài biến mất khỏi mọi nơi nhưng dữ liệu còn (phục vụ kiểm duyệt ở giai đoạn sau). */
    @Transactional
    public void delete(UUID actorId, UUID postId) {
        ownedLivePost(actorId, postId).setDeletedAt(clock.instant());
    }

    /** Ghim bài viết lên đầu trang cá nhân (tối đa 3 bài). */
    @Transactional
    public void pin(UUID actorId, UUID postId) {
        Post post = ownedLivePost(actorId, postId);
        if (post.isPinned()) {
            return;
        }
        if (posts.countPinnedByAuthor(actorId) >= 3) {
            throw new InvalidPostException("Chỉ được ghim tối đa 3 bài viết. Hãy bỏ ghim bài cũ trước.");
        }
        post.setPinned(true);
    }

    /** Bỏ ghim bài viết khỏi trang cá nhân. */
    @Transactional
    public void unpin(UUID actorId, UUID postId) {
        Post post = ownedLivePost(actorId, postId);
        post.setPinned(false);
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

    /** Quy tắc xem: tác giả phải còn hoạt động; công khai ai cũng xem; "bạn bè" chỉ bạn bè đã kết bạn xem được. */
    public boolean canView(Post post, UUID viewerId) {
        if (post.isDeleted() || !post.getAuthor().isActive()) {
            return false;
        }
        if (viewerId != null && viewerId.equals(post.getAuthor().getId())) {
            return true;
        }
        if (post.isScheduled()) {
            return false;
        }
        return switch (post.getVisibility()) {
            case PUBLIC -> true;
            case FRIENDS -> viewerId != null && friends.areFriends(post.getAuthor().getId(), viewerId);
            case PRIVATE -> false;
        };
    }

    // ---------- Nội bộ ----------

    private Post ownedLivePost(UUID actorId, UUID postId) {
        Post post = posts.findLive(postId).orElseThrow(PostNotFoundException::new);
        if (!post.getAuthor().getId().equals(actorId) || !post.getAuthor().isActive()) {
            throw new PostNotFoundException();
        }
        return post;
    }

    /** Bỏ trùng và chính tác giả; chỉ bạn bè mới gắn thẻ được, và người được gắn phải xem được bài. */
    private List<UUID> validTagIds(UUID authorId, List<UUID> requested, Visibility visibility) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = requested.stream().filter(id -> id != null && !id.equals(authorId)).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        if (visibility == Visibility.PRIVATE) {
            throw new InvalidPostException("Bài \"Chỉ mình tôi\" không gắn thẻ bạn bè được.");
        }
        if (ids.size() > MAX_TAGS) {
            throw new InvalidPostException("Mỗi bài gắn thẻ tối đa " + MAX_TAGS + " người.");
        }
        for (UUID id : ids) {
            if (!friends.areFriends(authorId, id) || !users.findById(id).filter(User::isActive).isPresent()) {
                throw new InvalidPostException("Chỉ gắn thẻ được những người đang là bạn bè của bạn.");
            }
        }
        return ids;
    }

    private void rejectBannedHashtags(String text) {
        List<String> banned = bannedHashtags.bannedIn(text);
        if (!banned.isEmpty()) {
            throw new InvalidPostException("Hashtag #" + banned.get(0) + " không được phép sử dụng.");
        }
    }

    private void validateSchedule(User author, Instant scheduledAt) {
        if (scheduledAt == null) {
            return;
        }
        if (!author.hasRole(com.aloute.model.user.Role.CREATOR)) {
            throw new InvalidPostException("Chỉ Creator mới hẹn giờ đăng bài được.");
        }
        Instant now = clock.instant();
        if (!scheduledAt.isAfter(now)) {
            throw new InvalidPostException("Giờ hẹn phải ở tương lai.");
        }
        if (scheduledAt.isAfter(now.plus(Duration.ofDays(MAX_SCHEDULE_DAYS)))) {
            throw new InvalidPostException("Chỉ hẹn giờ được trong vòng " + MAX_SCHEDULE_DAYS + " ngày.");
        }
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

    /** Cùng quy tắc bỏ ký tự điều khiển của {@link #cleanContent}, nhưng giới hạn ngắn hơn và cho phép rỗng. */
    private static String cleanCaption(String caption) {
        String text = caption == null ? "" : caption.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (text.codePointCount(0, text.length()) > Post.MAX_SHARE_CAPTION_LENGTH) {
            throw new InvalidPostException("Lời nhắn khi chia sẻ tối đa " + Post.MAX_SHARE_CAPTION_LENGTH + " ký tự.");
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
