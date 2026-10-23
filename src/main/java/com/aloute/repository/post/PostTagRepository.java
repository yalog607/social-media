package com.aloute.repository.post;

import com.aloute.model.post.PostTag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostTagRepository extends JpaRepository<PostTag, UUID> {

    /** Mọi thẻ của một bài (kể cả người đã bị khóa), để đối chiếu khi sửa bài. */
    @Query("select t from PostTag t join fetch t.taggedUser where t.post.id = :postId")
    List<PostTag> findByPostId(@Param("postId") UUID postId);

    /** Người được gắn thẻ (còn hoạt động) của nhiều bài cùng lúc, theo thứ tự được gắn. */
    @Query("""
            select t from PostTag t join fetch t.taggedUser u join fetch u.profile
            where t.post.id in :postIds and u.status = com.aloute.model.user.UserStatus.ACTIVE
            order by t.createdAt, t.id""")
    List<PostTag> findByPostIds(@Param("postIds") Collection<UUID> postIds);
}
