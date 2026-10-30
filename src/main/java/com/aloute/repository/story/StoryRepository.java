package com.aloute.repository.story;

import com.aloute.model.story.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StoryRepository extends JpaRepository<Story, UUID> {

    List<Story> findByUserIdAndExpiresAtAfterOrderByCreatedAtAsc(UUID userId, Instant now);

    List<Story> findByUserIdInAndExpiresAtAfterOrderByCreatedAtAsc(Collection<UUID> userIds, Instant now);

    void deleteByExpiresAtBefore(Instant now);
}

