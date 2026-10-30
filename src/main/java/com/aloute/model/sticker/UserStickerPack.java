package com.aloute.model.sticker;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sticker_packs", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_sticker_packs", columnNames = {"user_id", "pack_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class UserStickerPack {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_id", nullable = false)
    private StickerPack pack;

    @Column(name = "installed_at", nullable = false, updatable = false)
    private Instant installedAt = Instant.now();

    @Column(name = "grant_source", nullable = false)
    private String grantSource = "FREE";
    @Column(name = "granted_by")
    private UUID grantedBy;
    @Column(name = "grant_reason", length = 300)
    private String grantReason;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "revoked_by")
    private UUID revokedBy;

    public UserStickerPack(User user, StickerPack pack) {
        this.user = user;
        this.pack = pack;
        this.installedAt = Instant.now();
    }
}

