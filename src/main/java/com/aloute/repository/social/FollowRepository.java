package com.aloute.repository.social;

import com.aloute.model.social.Follow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    void deleteByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    void deleteByFollowerIdAndFolloweeIdIn(UUID followerId, java.util.Collection<UUID> followeeIds);

    long countByFollowerId(UUID followerId);

    long countByFolloweeId(UUID followeeId);

    @Query("select f from Follow f join fetch f.followee u join fetch u.profile where f.follower.id = :userId order by f.createdAt desc")
    List<Follow> findFollowing(@Param("userId") UUID userId);

    @Query("select f from Follow f join fetch f.follower u join fetch u.profile where f.followee.id = :userId order by f.createdAt desc")
    List<Follow> findFollowers(@Param("userId") UUID userId);
}
