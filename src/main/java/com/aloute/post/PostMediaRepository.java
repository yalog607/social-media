package com.aloute.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostMediaRepository extends JpaRepository<PostMedia, UUID> {

    /** Media của cả một trang bài trong MỘT truy vấn (tránh N+1), đã sắp đúng thứ tự trong từng bài. */
    @Query("select m from PostMedia m where m.post.id in :postIds order by m.post.id, m.sortOrder")
    List<PostMedia> findByPostIds(@Param("postIds") Collection<UUID> postIds);
}
