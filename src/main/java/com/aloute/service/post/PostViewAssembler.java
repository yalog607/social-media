package com.aloute.service.post;

import com.aloute.dto.category.CategoryView;
import com.aloute.dto.post.PollView;
import com.aloute.dto.post.PostView;
import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.model.chat.Conversation;
import com.aloute.model.chat.ConversationMember;
import com.aloute.model.chat.GroupRole;
import com.aloute.model.creator.BadgeTier;
import com.aloute.model.post.Post;
import com.aloute.model.post.PostKind;
import com.aloute.model.post.PostMedia;
import com.aloute.model.post.PostTag;
import com.aloute.model.user.Profile;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.chat.ConversationMemberRepository;
import com.aloute.repository.chat.ConversationRepository;
import com.aloute.repository.comment.CommentRepository;
import com.aloute.repository.post.PostMediaRepository;
import com.aloute.repository.post.PostRepository;
import com.aloute.repository.post.PostTagRepository;
import com.aloute.repository.social.FollowRepository;
import com.aloute.service.category.CategoryService;
import com.aloute.service.creator.PollService;
import com.aloute.service.creator.SeriesService;
import com.aloute.service.reaction.ReactionService;
import com.aloute.util.post.PostTextRenderer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final CategoryService categories;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final PollService polls;
    private final SeriesService seriesService;
    private final FollowRepository followRepository;
    private final Clock clock;

    public PostViewAssembler(PostRepository postRepository, PostMediaRepository mediaRepository,
                             ReactionService reactions, CommentRepository commentRepository,
                             PostTagRepository tagRepository,
                             CategoryService categories, ConversationRepository conversationRepository,
                             ConversationMemberRepository memberRepository, PollService polls,
                             SeriesService seriesService, FollowRepository followRepository, Clock clock) {
        this.polls = polls;
        this.seriesService = seriesService;
        this.categories = categories;
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.reactions = reactions;
        this.commentRepository = commentRepository;
        this.tagRepository = tagRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.followRepository = followRepository;
        this.clock = clock;
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
        Map<UUID, CategoryView> categoryById = categories.byIds(
                allPosts.stream().map(Post::getCategoryId).filter(java.util.Objects::nonNull).distinct().toList());

        Map<UUID, PollView> pollByPost = polls.viewsFor(
                allPosts.stream().filter(p -> p.getPostKind() == PostKind.POLL).toList(), viewerId);

        Map<UUID, String> seriesTitles = seriesService.titlesFor(
                allPosts.stream().map(Post::getSeriesId).filter(java.util.Objects::nonNull).distinct().toList());

        Map<UUID, BadgeTier> badgeByAuthor = loadBadges(allPosts);

        // Nạp thông tin nhóm và vai trò thành viên cho các bài viết thuộc nhóm
        Set<UUID> groupIds = allPosts.stream().map(Post::getGroupId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Conversation> conversationById = new HashMap<>();
        Map<UUID, Map<UUID, GroupRole>> memberRolesByGroup = new HashMap<>();
        if (!groupIds.isEmpty()) {
            for (Conversation c : conversationRepository.findAllById(groupIds)) {
                conversationById.put(c.getId(), c);
            }
            Set<UUID> userIds = allPosts.stream().filter(p -> p.getGroupId() != null).map(p -> p.getAuthor().getId()).collect(Collectors.toSet());
            if (viewerId != null) {
                userIds = new HashSet<>(userIds);
                userIds.add(viewerId);
            }
            for (ConversationMember cm : memberRepository.findByConversationIdInAndUserIdIn(groupIds, userIds)) {
                memberRolesByGroup.computeIfAbsent(cm.getConversation().getId(), k -> new HashMap<>())
                        .put(cm.getUser().getId(), cm.getRole());
            }
        }

        // Bước 1: dựng bài GỐC trước (sharedPost luôn null ở bước này — bài gốc không bao giờ tự nó là một chia sẻ khác)
        Map<UUID, PostView> baseViews = new HashMap<>();
        for (Post post : allPosts) {
            baseViews.put(post.getId(), toView(post, viewerId, mediaByPost, reactionsByPost, commentCountByPost, shareCountByPost,
                    taggedByPost, categoryById, conversationById, memberRolesByGroup, pollByPost, seriesTitles, badgeByAuthor, null));
        }

        // Bước 2: gắn bài gốc (đã dựng sẵn) vào các bài chia sẻ trong danh sách yêu cầu, giữ đúng thứ tự ban đầu
        List<PostView> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            UUID sharedId = sharedIdByPost.get(post.getId());
            if (sharedId == null) {
                result.add(baseViews.get(post.getId()));
            } else {
                PostView withoutShared = baseViews.get(post.getId());
                result.add(withoutShared != null ? withShared(withoutShared, sharedId, baseViews.get(sharedId)) : null);
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
                            user.getProfile().getAvatarUrl(), user.primaryRole(), user.getProfile().getAvatarFrame()));
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

    /** Tích theo follower cho các tác giả của {@code posts}, theo LÔ (một truy vấn group by). */
    private Map<UUID, BadgeTier> loadBadges(Collection<Post> posts) {
        Map<UUID, BadgeTier> result = new HashMap<>();
        Set<UUID> authorIds = posts.stream().map(p -> p.getAuthor().getId()).collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            return result;
        }
        Map<UUID, Long> followerCounts = new HashMap<>();
        for (FollowRepository.FollowerCount row : followRepository.countByFolloweeIds(authorIds)) {
            followerCounts.put(row.getUserId(), row.getTotal());
        }
        LocalDate today = LocalDate.now(clock);
        for (Post post : posts) {
            User author = post.getAuthor();
            long followerCount = followerCounts.getOrDefault(author.getId(), 0L);
            result.put(author.getId(), BadgeTier.of(followerCount, author.getProfile().getBirthDate(), today, author.primaryRole()));
        }
        return result;
    }

    private static PostView toView(Post post, UUID viewerId, Map<UUID, List<PostView.MediaView>> mediaByPost,
                                   Map<UUID, ReactionSummary> reactionsByPost, Map<UUID, Long> commentCountByPost,
                                   Map<UUID, Long> shareCountByPost,
                                   Map<UUID, List<PostView.AuthorView>> taggedByPost,
                                   Map<UUID, CategoryView> categoryById,
                                   Map<UUID, Conversation> conversationById,
                                   Map<UUID, Map<UUID, GroupRole>> memberRolesByGroup,
                                   Map<UUID, PollView> pollByPost, Map<UUID, String> seriesTitles,
                                   Map<UUID, BadgeTier> badgeByAuthor,
                                   PostView sharedPost) {
        User author = post.getAuthor();
        Profile profile = author.getProfile();
        boolean mine = viewerId != null && viewerId.equals(author.getId());
        UUID sharedId = sharedPost != null ? sharedPost.id() : null;
        PostView.AuthorView authorView;
        if (post.isAnonymous()) {
            if (mine) {
                authorView = new PostView.AuthorView(author.getId(), author.getUsername(),
                        profile.getDisplayName() + " (Bạn - Ẩn danh)", profile.getAvatarUrl(), author.primaryRole());
            } else {
                authorView = new PostView.AuthorView(null, "", "Người tham gia ẩn danh", null, Role.USER);
            }
        } else {
            authorView = new PostView.AuthorView(author.getId(), author.getUsername(), profile.getDisplayName(),
                    profile.getAvatarUrl(), author.primaryRole(), profile.getAvatarFrame(),
                    badgeByAuthor.getOrDefault(author.getId(), BadgeTier.NONE));
        }

        PostView.GroupInfo groupInfo = null;
        if (post.getGroupId() != null) {
            Conversation conv = conversationById.get(post.getGroupId());
            if (conv != null) {
                GroupRole authorRole = memberRolesByGroup.getOrDefault(post.getGroupId(), Map.of()).get(post.getAuthor().getId());
                GroupRole viewerRole = viewerId != null ? memberRolesByGroup.getOrDefault(post.getGroupId(), Map.of()).get(viewerId) : null;
                groupInfo = new PostView.GroupInfo(conv.getId(), conv.getTitle(), conv.getAvatarUrl(), authorRole, viewerRole);
            }
        }

        return new PostView(
                post.getId(),
                authorView,
                PostTextRenderer.toSafeHtml(post.getContent()),
                post.getVisibility(),
                post.getCreatedAt(),
                post.getEditedAt() != null,
                mediaByPost.getOrDefault(post.getId(), List.of()),
                mine,
                mine ? post.getContent() : null,
                reactionsByPost.getOrDefault(post.getId(), ReactionSummary.EMPTY),
                commentCountByPost.getOrDefault(post.getId(), 0L),
                shareCountByPost.getOrDefault(post.getId(), 0L),
                sharedId,
                sharedPost,
                taggedByPost.getOrDefault(post.getId(), List.of()),
                post.getCategoryId() == null ? null : categoryById.get(post.getCategoryId()),
                post.isPinned(), post.isAnonymous(), post.getGroupId(), groupInfo,
                post.getPostKind(), pollByPost.get(post.getId()),
                post.getSeriesId() != null && seriesTitles.containsKey(post.getSeriesId())
                        ? new PostView.SeriesInfo(post.getSeriesId(), seriesTitles.get(post.getSeriesId())) : null);
    }

    /** Bản sao của {@code view} với {@code sharedPost} được gắn (bản thân {@code view} được dựng như bài thường ở bước 1). */
    private static PostView withShared(PostView view, UUID sharedPostId, PostView sharedPost) {
        return new PostView(view.id(), view.author(), view.contentHtml(), view.visibility(), view.createdAt(),
                view.edited(), view.media(), view.mine(), view.rawContent(), view.reactions(), view.commentCount(),
                view.shareCount(), sharedPostId, sharedPost, view.tagged(),
                view.category(), view.isPinned(), view.isAnonymous(), view.groupId(), view.group(), view.kind(), view.poll(), view.series());
    }
}
