package com.aloute.repository.comment;

import com.aloute.model.comment.Comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Bình luận (gốc lẫn trả lời, kể cả đã xóa mềm) của một bài, cũ nhất trước, tác giả đã nạp sẵn. */
    @Query("""
            select c from Comment c join fetch c.author a join fetch a.profile
            where c.post.id = :postId
            order by c.createdAt asc""")
    List<Comment> findByPostId(@Param("postId") UUID postId);

    /** Để kiểm tra quyền xóa: cần tác giả bình luận và tác giả bài. */
    @Query("select c from Comment c join fetch c.author join fetch c.post p join fetch p.author where c.id = :id")
    Optional<Comment> findWithAuthors(@Param("id") UUID id);

    /** Số bình luận CHƯA xóa, cho cả một trang bài trong một truy vấn. */
    @Query("""
            select c.post.id as postId, count(c) as total from Comment c
            where c.post.id in :postIds and c.deletedAt is null
            group by c.post.id""")
    List<PostCommentCount> countsByPostIds(@Param("postIds") Collection<UUID> postIds);

    interface PostCommentCount {
        UUID getPostId();

        long getTotal();
    }
}
