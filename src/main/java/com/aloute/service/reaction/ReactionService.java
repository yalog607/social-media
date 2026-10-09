package com.aloute.service.reaction;

import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.dto.reaction.Reactor;
import com.aloute.model.reaction.Reaction;
import com.aloute.model.reaction.ReactionType;
import com.aloute.repository.reaction.ReactionRepository;

import com.aloute.model.common.RateAction;
import com.aloute.service.common.RateLimiter;
import com.aloute.service.notification.NotificationService;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thả/đổi/bỏ cảm xúc cho một bài. Mỗi người giữ đúng một cảm xúc cho mỗi bài (ràng buộc duy nhất ở DB):
 * thả loại khác thì đổi, thả lại đúng loại đang có thì bỏ.
 */
@Service
public class ReactionService {

    private final ReactionRepository reactions;
    public static final int MAX_REACTORS = 200;

    private final PostService posts;
    private final UserRepository users;
    private final RateLimiter rateLimiter;
    private final NotificationService notifications;

    public ReactionService(ReactionRepository reactions, PostService posts, UserRepository users,
                           RateLimiter rateLimiter, NotificationService notifications) {
        this.reactions = reactions;
        this.posts = posts;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
    }

    /** @throws com.aloute.exception.post.PostNotFoundException bài không tồn tại hoặc không xem được */
    @Transactional
    public ReactionSummary toggle(UUID userId, UUID postId, ReactionType type) {
        rateLimiter.check(RateAction.REACTION, userId);
        Post post = posts.getVisible(postId, userId);

        Optional<Reaction> existing = reactions.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent() && existing.get().getType() == type) {
            reactions.delete(existing.get());
        } else {
            Reaction reaction = existing.orElseGet(Reaction::new);
            if (reaction.getId() == null) {
                reaction.setPost(post);
                reaction.setUser(users.getReferenceById(userId));
            }
            reaction.setType(type);
            reactions.save(reaction);
            notifications.postReacted(userId, post);
        }
        return summarize(postId, userId);
    }

    /**
     * Những người đã thả cảm xúc cho bài (tối đa {@value #MAX_REACTORS}, mới nhất trước).
     *
     * @throws com.aloute.exception.post.PostNotFoundException bài không tồn tại hoặc {@code viewerId} không được xem
     */
    @Transactional(readOnly = true)
    public java.util.List<Reactor> reactors(UUID postId, UUID viewerId) {
        posts.getVisible(postId, viewerId);
        return reactions.findReactors(postId, org.springframework.data.domain.PageRequest.of(0, MAX_REACTORS)).stream()
                .map(r -> new Reactor(com.aloute.dto.chat.AuthorViews.of(r.getUser()), r.getType())).toList();
    }

    @Transactional(readOnly = true)
    public ReactionSummary summarize(UUID postId, UUID viewerId) {
        Map<ReactionType, Long> counts = ReactionSummary.emptyCounts();
        for (ReactionRepository.PostReactionCount row : reactions.countsByPostIds(java.util.List.of(postId))) {
            counts.put(row.getType(), row.getTotal());
        }
        ReactionType mine = viewerId == null ? null
                : reactions.findByPostIdAndUserId(postId, viewerId).map(Reaction::getType).orElse(null);
        return new ReactionSummary(counts, mine);
    }

    /** Số cảm xúc và loại của người xem cho cả một trang bài, trong hai truy vấn (không phải một truy vấn mỗi bài). */
    @Transactional(readOnly = true)
    public Map<UUID, ReactionSummary> summarizeAll(Collection<UUID> postIds, UUID viewerId) {
        Map<UUID, Map<ReactionType, Long>> countsByPost = new java.util.HashMap<>();
        for (ReactionRepository.PostReactionCount row : reactions.countsByPostIds(postIds)) {
            countsByPost.computeIfAbsent(row.getPostId(), id -> ReactionSummary.emptyCounts()).put(row.getType(), row.getTotal());
        }
        Map<UUID, ReactionType> mineByPost = new java.util.HashMap<>();
        if (viewerId != null) {
            for (ReactionRepository.MyPostReaction row : reactions.myReactions(postIds, viewerId)) {
                mineByPost.put(row.getPostId(), row.getType());
            }
        }
        Map<UUID, ReactionSummary> result = new java.util.HashMap<>();
        for (UUID postId : postIds) {
            result.put(postId, new ReactionSummary(
                    countsByPost.getOrDefault(postId, ReactionSummary.emptyCounts()), mineByPost.get(postId)));
        }
        return result;
    }
}
