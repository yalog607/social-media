package com.aloute.repository.group;

import com.aloute.model.group.GroupPostReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupPostReportRepository extends JpaRepository<GroupPostReport, UUID> {

    @Query("select r from GroupPostReport r join fetch r.post p join fetch r.reporter rep join fetch rep.profile where r.group.id = :groupId order by r.createdAt desc")
    List<GroupPostReport> findByGroupIdWithDetails(@Param("groupId") UUID groupId);

    boolean existsByPostIdAndReporterId(UUID postId, UUID reporterId);

    long countByGroupId(UUID groupId);

    void deleteByPostId(UUID postId);
}
