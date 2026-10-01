package com.aloute.post;

import com.aloute.category.CategoryService;
import com.aloute.category.CategoryView;
import com.aloute.comment.CommentRepository;
import com.aloute.reaction.ReactionService;
import com.aloute.reaction.ReactionSummary;
import com.aloute.user.Profile;
import com.aloute.user.User;
import com.aloute.wallet.PaidContentAccess;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chuyển một danh sách bài thành {@link PostView}. Mọi dữ liệu quan hệ (media, cảm xúc, bình luận, chia sẻ,
 * và bài GỐC của các bài chia sẻ) được nạp theo LÔ cho cả danh sách, không truy vấn riêng cho từng bài.
 */
@Component
public class PostViewAssembler {

    private final PostRepository postRepository;
    private final PostMediaRepository mediaRepository;
    private final ReactionService reactions;
    private final CommentRepository commentRepository;
    private final PostTagRepository tagRepository;
    private final PaidContentAccess paidAccess;
    private final CategoryService categories;

    public PostViewAssembler(PostRepository postRepository, PostMediaRepository mediaRepository,
                             ReactionService reactions, CommentRepository commentRepository,
                             PostTagRepository tagRepository, PaidContentAccess paidAccess,
                             CategoryService categories) {
        this.categories = categories;
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.reactions = reactions;
        this.commentRepository = commentRepository;
        this.tagRepository = tagRepository;
        this.paidAccess = paidAccess;
    }

    /** @param viewerId người xem, null nếu là khách (để đánh dấu bài "của tôi") */
    @Transactional(readOnly = true)
    public List<PostView> assemble(List<Post> posts, UUID viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        // Truy cập post.getSharedPost().getId() không tự nạp cả thực thể (chỉ đọc khóa ngoại đã có sẵn trên bản ghi).
        Map<UUID, UUID> sharedIdByPost = new LinkedHashMap<>();
        for (Post post : posts) {
            if (post.getSharedPost() != null) {
                sharedIdByPost.put(post.getId(), post.getSharedPost().getId());
            }
        }
        List<Post> originals = sharedIdByPost.isEmpty() ? List.of() : postRepository.findLiveByIds(sharedIdByPost.values());

        List<Post> allPosts = new ArrayList<>(posts.size() + originals.size());
        allPosts.addAll(posts);
        for (Post original : originals) {
            if (!containsId(posts, original.getId())) {
                allPosts.add(original);
            }
        }
        List<UUID> allIds = allPosts.stream().map(Post::getId).toList();

        Map<UUID, List<PostView.MediaView>> mediaByPost = loadMedia(allIds);
        Map<UUID, ReactionSummary> reactionsByPost = reactions.summarizeAll(allIds, viewerId);
        Map<UUID, Long> commentCountByPost = loadCommentCounts(allIds);
        Map<UUID, Long> shareCountByPost = loadShareCounts(allIds);
        Map<UUID, List<PostView.AuthorView>> taggedByPost = loadTagged(allIds);
        // Chỉ tra cứu mở khóa khi trong trang có bài trả phí (bảng tin thường không phát sinh thêm truy vấn nào)
        List<UUID> paidIds = allPosts.stream().filter(p -> p.getUnlockPrice() != null).map(Post::getId).toList();
        Set<UUID> unlocked = paidAccess.unlockedAmong(viewerId, paidIds);
        Map<UUID, CategoryView> categoryById = categories.byIds(
                allPosts.stream().map(Post::getCategoryId).filter(java.util.Objects::nonNull).distinct().toList());

        // Bước 1: dựng bài GỐC trước (sharedPost luôn null ở bước này — bài gốc không bao giờ tự nó là một chia sẻ khác)
        Map<UUID, PostView> baseViews = new HashMap<>();
        for (Post post : allPosts) {
            baseViews.put(post.getId(), toView(post, viewerId, mediaByPost, reactionsByPost, commentCountByPost, shareCountByPost, taggedByPost, unlocked, categoryById, null));
        }

        // Bước 2: gắn bài gốc (đã dựng sẵn) vào các bài chia sẻ trong danh sách yêu cầu, giữ đúng thứ tự ban đầu
        List<PostView> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            UUID sharedId = sharedIdByPost.get(post.getId());
            if (sharedId == null) {
                result.add(baseViews.get(post.getId()));
            } else {
                PostView withoutShared = baseViews.get(post.getId());
                result.add(withShared(withoutShared, sharedId, baseViews.get(sharedId)));
            }
        }
        return result;
    }

    private static boolean containsId(List<Post> posts, UUID id) {
        for (Post post : posts) {
            if (post.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private Map<UUID, List<PostView.MediaView>> loadMedia(Collection<UUID> postIds) {
        Map<UUID, List<PostView.MediaView>> byPost = new HashMap<>();
        for (PostMedia item : mediaRepository.findByPostIds(postIds)) {
            byPost.computeIfAbsent(item.getPost().getId(), id -> new ArrayList<>())
                    .add(new PostView.MediaView(item.getKind(), item.getUrl(), item.getContentType()));
        }
        return byPost;
    }

    private Map<UUID, List<PostView.AuthorView>> loadTagged(Collection<UUID> postIds) {
        Map<UUID, List<PostView.AuthorView>> byPost = new HashMap<>();
        for (PostTag tag : tagRepository.findByPostIds(postIds)) {
            User user = tag.getTaggedUser();
            byPost.computeIfAbsent(tag.getPost().getId(), id -> new ArrayList<>())
                    .add(new PostView.AuthorView(user.getId(), user.getUsername(), user.getProfile().getDisplayName(),
                            user.getProfile().getAvatarUrl(), user.primaryRole()));
        }
        return byPost;
    }

    private Map<UUID, Long> loadCommentCounts(Collection<UUID> postIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (CommentRepository.PostCommentCount row : commentRepository.countsByPostIds(postIds)) {
            counts.put(row.getPostId(), row.getTotal());
        }
        return counts;
    }

    private Map<UUID, Long> loadShareCounts(Collection<UUID> postIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (PostRepository.PostShareCount row : postRepository.shareCountsByPostIds(postIds)) {
            counts.put(row.getPostId(), row.getTotal());
        }
        return counts;
    }

    private static PostView toView(Post post, UUID viewerId, Map<UUID, List<PostView.MediaView>> mediaByPost,
                                   Map<UUID, ReactionSummary> reactionsByPost, Map<UUID, Long> commentCountByPost,
                                   Map<UUID, Long> shareCountByPost,
                                   Map<UUID, List<PostView.AuthorView>> taggedByPost, Set<UUID> unlocked,
                                   Map<UUID, CategoryView> categoryById, PostView sharedPost) {
        User author = post.getAuthor();
        Profile profile = author.getProfile();
        boolean mine = viewerId != null && viewerId.equals(author.getId());
        UUID sharedId = sharedPost != null ? sharedPost.id() : null;
        boolean locked = post.getUnlockPrice() != null && !mine && !unlocked.contains(post.getId());
        return new PostView(
                post.getId(),
                new PostView.AuthorView(author.getId(), author.getUsername(), profile.getDisplayName(),
                        profile.getAvatarUrl(), author.primaryRole()),
                locked ? "" : PostTextRenderer.toSafeHtml(post.getContent()),
                post.getVisibility(),
                post.getCreatedAt(),
                post.getEditedAt() != null,
                locked ? List.of() : mediaByPost.getOrDefault(post.getId(), List.of()),
                mine,
                mine ? post.getContent() : null,
                reactionsByPost.getOrDefault(post.getId(), ReactionSummary.EMPTY),
                commentCountByPost.getOrDefault(post.getId(), 0L),
                shareCountByPost.getOrDefault(post.getId(), 0L),
                sharedId,
                sharedPost,
                taggedByPost.getOrDefault(post.getId(), List.of()),
                post.getUnlockPrice(),
                locked,
                post.getCategoryId() == null ? null : categoryById.get(post.getCategoryId()));
    }

    /** Bản sao của {@code view} với {@code sharedPost} được gắn (bản thân {@code view} được dựng như bài thường ở bước 1). */
    private static PostView withShared(PostView view, UUID sharedPostId, PostView sharedPost) {
        return new PostView(view.id(), view.author(), view.contentHtml(), view.visibility(), view.createdAt(),
                view.edited(), view.media(), view.mine(), view.rawContent(), view.reactions(), view.commentCount(),
                view.shareCount(), sharedPostId, sharedPost, view.tagged(),
                view.unlockPrice(), view.locked(), view.category());
    }
}
