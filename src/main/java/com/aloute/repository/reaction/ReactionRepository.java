package com.aloute.repository.reaction;

import com.aloute.model.reaction.Reaction;
import com.aloute.model.reaction.ReactionType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    Optional<Reaction> findByPostIdAndUserId(UUID postId, UUID userId);

    /** Số lượng mỗi loại cảm xúc, cho cả một trang bài trong MỘT truy vấn. */
    @Query("""
            select r.post.id as postId, r.type as type, count(r) as total
            from Reaction r where r.post.id in :postIds
            group by r.post.id, r.type""")
    List<PostReactionCount> countsByPostIds(@Param("postIds") Collection<UUID> postIds);

    /** Cảm xúc mà {@code userId} đã thả, cho cả một trang bài trong MỘT truy vấn. */
    @Query("select r.post.id as postId, r.type as type from Reaction r where r.post.id in :postIds and r.user.id = :userId")
    List<MyPostReaction> myReactions(@Param("postIds") Collection<UUID> postIds, @Param("userId") UUID userId);

    /** Người đã thả cảm xúc cho một bài (còn hoạt động), mới nhất trước. */
    @Query("""
            select r from Reaction r join fetch r.user u join fetch u.profile
            where r.post.id = :postId and u.status = com.aloute.model.user.UserStatus.ACTIVE
            order by r.createdAt desc, r.id desc""")
    List<Reaction> findReactors(@Param("postId") UUID postId, org.springframework.data.domain.Pageable pageable);

    interface PostReactionCount {
        UUID getPostId();

        ReactionType getType();

        long getTotal();
    }

    interface MyPostReaction {
        UUID getPostId();

        ReactionType getType();
    }
}
