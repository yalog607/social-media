package com.aloute.repository.comment;

import com.aloute.model.comment.CommentReaction;
import com.aloute.model.reaction.ReactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, UUID> {

    Optional<CommentReaction> findByCommentIdAndUserId(UUID commentId, UUID userId);

    /** Số lượng mỗi loại cảm xúc cho danh sách bình luận trong MỘT truy vấn. */
    @Query("""
            select r.comment.id as commentId, r.type as type, count(r) as total
            from CommentReaction r where r.comment.id in :commentIds
            group by r.comment.id, r.type""")
    List<CommentReactionCount> countsByCommentIds(@Param("commentIds") Collection<UUID> commentIds);

    /** Cảm xúc mà {@code userId} đã thả cho danh sách bình luận trong MỘT truy vấn. */
    @Query("select r.comment.id as commentId, r.type as type from CommentReaction r where r.comment.id in :commentIds and r.user.id = :userId")
    List<MyCommentReaction> myReactions(@Param("commentIds") Collection<UUID> commentIds, @Param("userId") UUID userId);

    /** Người đã thả cảm xúc cho một bình luận (còn hoạt động), mới nhất trước. */
    @Query("""
            select r from CommentReaction r join fetch r.user u join fetch u.profile
            where r.comment.id = :commentId and u.status = com.aloute.model.user.UserStatus.ACTIVE
            order by r.createdAt desc, r.id desc""")
    List<CommentReaction> findReactors(@Param("commentId") UUID commentId, Pageable pageable);

    interface CommentReactionCount {
        UUID getCommentId();

        ReactionType getType();

        long getTotal();
    }

    interface MyCommentReaction {
        UUID getCommentId();

        ReactionType getType();
    }
}
