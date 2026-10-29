package com.aloute.repository.story;

import com.aloute.model.story.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StoryViewRepository extends JpaRepository<StoryView, UUID> {

    boolean existsByStoryIdAndViewerId(UUID storyId, UUID viewerId);

    Optional<StoryView> findByStoryIdAndViewerId(UUID storyId, UUID viewerId);

    long countByStoryId(UUID storyId);

    List<StoryView> findByStoryIdOrderByViewedAtDesc(UUID storyId);

    @Query("SELECT v.reaction FROM StoryView v WHERE v.story.id = :storyId AND v.reaction IS NOT NULL")
    List<String> findReactionsByStoryId(@Param("storyId") UUID storyId);

    @Query("SELECT v.story.id FROM StoryView v WHERE v.viewer.id = :viewerId AND v.story.id IN :storyIds")
    Set<UUID> findViewedStoryIds(@Param("viewerId") UUID viewerId, @Param("storyIds") Collection<UUID> storyIds);
}

