package com.aloute.repository.mood;

import com.aloute.model.mood.DailyMood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyMoodRepository extends JpaRepository<DailyMood, UUID> {

    Optional<DailyMood> findByUserId(UUID userId);

    @Query("""
            select m from DailyMood m
            join fetch m.user u
            join fetch u.profile p
            where m.user.id = :userId and m.expiresAt > :now
            """)
    Optional<DailyMood> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            select m from DailyMood m
            join fetch m.user u
            join fetch u.profile p
            where m.user.id in :userIds and m.expiresAt > :now
            order by m.updatedAt desc
            """)
    List<DailyMood> findActiveByUserIds(@Param("userIds") Collection<UUID> userIds, @Param("now") Instant now);

    void deleteByUserId(UUID userId);
}
