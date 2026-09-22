package com.aloute.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
public class Profile {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String bio;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "cover_url")
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false)
    private Visibility profileVisibility = Visibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_post_visibility", nullable = false)
    private Visibility defaultPostVisibility = Visibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_permission", nullable = false)
    private MessagePermission messagePermission = MessagePermission.EVERYONE;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
