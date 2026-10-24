package com.aloute.service.comment;

import com.aloute.dto.chat.AuthorViews;
import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.dto.reaction.Reactor;
import com.aloute.exception.comment.CommentNotFoundException;
import com.aloute.model.comment.Comment;
import com.aloute.model.comment.CommentReaction;
import com.aloute.model.common.RateAction;
import com.aloute.model.reaction.ReactionType;
import com.aloute.repository.comment.CommentReactionRepository;
import com.aloute.repository.comment.CommentRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.common.RateLimiter;
import com.aloute.service.notification.NotificationService;
import com.aloute.service.post.PostService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thả/đổi/bỏ cảm xúc cho bình luận.
 */
@Service
public class CommentReactionService {

    public static final int MAX_REACTORS = 200;

    private final CommentReactionRepository reactions;
    private final CommentRepository comments;
    private final PostService posts;
    private final UserRepository users;
    private final RateLimiter rateLimiter;
    private final NotificationService notifications;

    public CommentReactionService(CommentReactionRepository reactions, CommentRepository comments,
                                  PostService posts, UserRepository users,
                                  RateLimiter rateLimiter, NotificationService notifications) {
        this.reactions = reactions;
        this.comments = comments;
        this.posts = posts;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
    }

    @Transactional
    public ReactionSummary toggle(UUID userId, UUID commentId, ReactionType type) {
        rateLimiter.check(RateAction.REACTION, userId);
        Comment comment = comments.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(CommentNotFoundException::new);
        posts.getVisible(comment.getPost().getId(), userId);

        Optional<CommentReaction> existing = reactions.findByCommentIdAndUserId(commentId, userId);
        if (existing.isPresent() && existing.get().getType() == type) {
            reactions.delete(existing.get());
        } else {
            CommentReaction reaction = existing.orElseGet(CommentReaction::new);
            if (reaction.getId() == null) {
                reaction.setComment(comment);
                reaction.setUser(users.getReferenceById(userId));
            }
            reaction.setType(type);
            reactions.save(reaction);
            if (!comment.getAuthor().getId().equals(userId)) {
                notifications.commentReacted(userId, comment);
            }
        }
        return summarize(commentId, userId);
    }

    @Transactional(readOnly = true)
    public List<Reactor> reactors(UUID commentId, UUID viewerId) {
        Comment comment = comments.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(CommentNotFoundException::new);
        posts.getVisible(comment.getPost().getId(), viewerId);
        return reactions.findReactors(commentId, PageRequest.of(0, MAX_REACTORS)).stream()
                .map(r -> new Reactor(AuthorViews.of(r.getUser()), r.getType())).toList();
    }

    @Transactional(readOnly = true)
    public ReactionSummary summarize(UUID commentId, UUID viewerId) {
        Map<ReactionType, Long> counts = ReactionSummary.emptyCounts();
        for (CommentReactionRepository.CommentReactionCount row : reactions.countsByCommentIds(List.of(commentId))) {
            counts.put(row.getType(), row.getTotal());
        }
        ReactionType mine = viewerId == null ? null
                : reactions.findByCommentIdAndUserId(commentId, viewerId).map(CommentReaction::getType).orElse(null);
        return new ReactionSummary(counts, mine);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ReactionSummary> summarizeAll(Collection<UUID> commentIds, UUID viewerId) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<ReactionType, Long>> countsByComment = new HashMap<>();
        for (CommentReactionRepository.CommentReactionCount row : reactions.countsByCommentIds(commentIds)) {
            countsByComment.computeIfAbsent(row.getCommentId(), id -> ReactionSummary.emptyCounts()).put(row.getType(), row.getTotal());
        }
        Map<UUID, ReactionType> mineByComment = new HashMap<>();
        if (viewerId != null) {
            for (CommentReactionRepository.MyCommentReaction row : reactions.myReactions(commentIds, viewerId)) {
                mineByComment.put(row.getCommentId(), row.getType());
            }
        }
        Map<UUID, ReactionSummary> result = new HashMap<>();
        for (UUID commentId : commentIds) {
            result.put(commentId, new ReactionSummary(
                    countsByComment.getOrDefault(commentId, ReactionSummary.emptyCounts()), mineByComment.get(commentId)));
        }
        return result;
    }
}
