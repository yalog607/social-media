package com.aloute.post;

import com.aloute.user.Visibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mọi truy vấn danh sách đều loại bài đã xóa mềm và bài của người dùng không còn ACTIVE, sắp theo
 * {@code (created_at, id)} giảm dần, và nhận con trỏ {@code (cursorTime, cursorId)} là bản ghi cuối trang trước
 * (trang đầu dùng con trỏ "vô cực" của {@code Cursor}, để không phải xử lý tham số null).
 */
public interface PostRepository extends JpaRepository<Post, UUID> {

    /** Bài chưa bị xóa kèm tác giả và hồ sơ. Việc kiểm tra quyền xem do {@code PostService} làm. */
    @Query("""
            select p from Post p join fetch p.author a join fetch a.profile
            where p.id = :id and p.deletedAt is null""")
    Optional<Post> findLive(@Param("id") UUID id);

    /** Bảng tin: bài công khai của mọi người + mọi bài của chính người xem (kể cả riêng tư). */
    @Query("""
            select p from Post p join fetch p.author a join fetch a.profile
            where p.deletedAt is null and a.status = com.aloute.user.UserStatus.ACTIVE
              and (p.visibility = com.aloute.user.Visibility.PUBLIC or a.id = :viewerId)
              and (p.createdAt < :cursorTime or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc""")
    List<Post> feed(@Param("viewerId") UUID viewerId, @Param("cursorTime") Instant cursorTime,
                    @Param("cursorId") UUID cursorId, Pageable pageable);

    /** Bài của một tác giả, giới hạn theo các mức quyền xem mà người xem được thấy. */
    @Query("""
            select p from Post p join fetch p.author a join fetch a.profile
            where p.deletedAt is null and a.status = com.aloute.user.UserStatus.ACTIVE and a.id = :authorId
              and p.visibility in :visibilities
              and (p.createdAt < :cursorTime or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc""")
    List<Post> byAuthor(@Param("authorId") UUID authorId, @Param("visibilities") Collection<Visibility> visibilities,
                        @Param("cursorTime") Instant cursorTime, @Param("cursorId") UUID cursorId, Pageable pageable);
}
