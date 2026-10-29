package com.aloute.model.story;

import com.aloute.model.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Ghi nhận một lượt xem Story của người dùng. */
@Entity
@Table(name = "story_views", uniqueConstraints = {
        @UniqueConstraint(name = "uk_story_viewer", columnNames = {"story_id", "viewer_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class StoryView {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id", nullable = false)
    private User viewer;

    @CreationTimestamp
    @Column(name = "viewed_at", nullable = false, updatable = false)
    private Instant viewedAt;

    @Column(name = "reaction", length = 500)
    private String reaction;

    public StoryView(Story story, User viewer) {
        this.story = story;
        this.viewer = viewer;
    }

    public StoryView(Story story, User viewer, String reaction) {
        this.story = story;
        this.viewer = viewer;
        this.reaction = reaction;
    }
}

