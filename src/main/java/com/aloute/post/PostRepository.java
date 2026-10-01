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

    /** Nhiều bài cùng lúc, dùng để dựng các bài GỐC được chia sẻ (một truy vấn cho cả trang, không phải một truy vấn mỗi bài). */
    @Query("""
            select p from Post p join fetch p.author a join fetch a.profile
            where p.id in :ids and p.deletedAt is null and p.scheduledAt is null""")
    List<Post> findLiveByIds(@Param("ids") Collection<UUID> ids);

    /** Số lượt chia sẻ CÒN SỐNG của mỗi bài, cho cả một trang bài trong một truy vấn. */
    @Query("""
            select p.sharedPost.id as postId, count(p) as total from Post p
            where p.sharedPost.id in :ids and p.deletedAt is null and p.scheduledAt is null
            group by p.sharedPost.id""")
    List<PostShareCount> shareCountsByPostIds(@Param("ids") Collection<UUID> ids);

    interface PostShareCount {
        UUID getPostId();

        long getTotal();
    }

    /** Cùng bạn bè (kết bạn đã được chấp nhận) hay không — dùng lại ở cả bảng tin lẫn trang cá nhân. */
    String IS_FRIEND = """
            exists (select 1 from com.aloute.social.Friendship f
                    where f.status = com.aloute.social.FriendshipStatus.ACCEPTED
                      and ((f.userA.id = a.id and f.userB.id = :viewerId) or (f.userB.id = a.id and f.userA.id = :viewerId)))""";

    /** Bảng tin: bài công khai của mọi người + bài "chỉ bạn bè" của bạn bè + mọi bài của chính người xem. */
    @Query("""
            select p from Post p join fetch p.author a join fetch a.profile
            where p.deletedAt is null and p.scheduledAt is null and a.status = com.aloute.user.UserStatus.ACTIVE
              and (p.visibility = com.aloute.user.Visibility.PUBLIC or a.id = :viewerId
                   or (p.visibility = com.aloute.user.Visibility.FRIENDS and """ + " " + IS_FRIEND + """
              ))
              and (p.createdAt < :cursorTime or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc""")
    List<Post> feed(@Param("viewerId") UUID viewerId, @Param("cursorTime") Instant cursorTime,
                    @Param("cursorId") UUID cursorId, Pageable pageable);

    /**
     * Bài của một tác giả, giới hạn theo các mức quyền xem mà người xem được thấy ({@code visibilities} là
     * PUBLIC-only cho người lạ hoặc mọi mức cho chính chủ); bài "chỉ bạn bè" hiện thêm nếu người xem là bạn bè.
     */
    @Query("""
            select p from Post p join fetch p.author a join fetch a.profile
            where p.deletedAt is null and p.scheduledAt is null and a.status = com.aloute.user.UserStatus.ACTIVE and a.id = :authorId
              and (p.visibility in :visibilities or (p.visibility = com.aloute.user.Visibility.FRIENDS and """ + " " + IS_FRIEND + """
              ))
              and (p.createdAt < :cursorTime or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc""")
    List<Post> byAuthor(@Param("authorId") UUID authorId, @Param("visibilities") Collection<Visibility> visibilities,
                        @Param("viewerId") UUID viewerId,
                        @Param("cursorTime") Instant cursorTime, @Param("cursorId") UUID cursorId, Pageable pageable);

    /** Bài công khai chứa một hashtag (thẻ đã ở dạng chuẩn hóa), mới nhất trước. */
    @Query("""
            select p from Post p join fetch p.author a join fetch a.profile join p.hashtags h
            where h = :tag and p.deletedAt is null and p.scheduledAt is null and a.status = com.aloute.user.UserStatus.ACTIVE
              and p.visibility = com.aloute.user.Visibility.PUBLIC
              and (p.createdAt < :cursorTime or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc""")
    List<Post> byHashtag(@Param("tag") String tag, @Param("cursorTime") Instant cursorTime,
                        @Param("cursorId") UUID cursorId, Pageable pageable);

    /**
     * ID bài công khai khớp {@code escapedQuery} (đã escape ký tự đặc biệt của ILIKE), gần đúng nhất trước
     * (điểm giống nhau theo trigram), rồi tới mới nhất. Chỉ trả về ID: nội dung đầy đủ được nạp theo lô ở
     * {@code findLiveByIds} để dùng chung đường hydrate với các nơi khác (không N+1).
     */
    @Query(value = """
            select p.id from posts p join users u on u.id = p.author_id
            where p.deleted_at is null and p.scheduled_at is null and p.visibility = 'PUBLIC' and u.status = 'ACTIVE'
              and p.search_text ilike '%' || :escapedQuery || '%' escape '\\'
            order by similarity(p.search_text, :rawQuery) desc, p.created_at desc
            limit :limit""", nativeQuery = true)
    List<UUID> searchPublicIds(@Param("escapedQuery") String escapedQuery, @Param("rawQuery") String rawQuery,
                               @Param("limit") int limit);

    /** Bài hẹn giờ chưa đăng của một tác giả, sớm nhất trước. */
    @Query("""
            select p from Post p join fetch p.author a join fetch a.profile
            where p.author.id = :authorId and p.deletedAt is null and p.scheduledAt is not null
            order by p.scheduledAt, p.id""")
    List<Post> findScheduledByAuthor(@Param("authorId") UUID authorId);
}
