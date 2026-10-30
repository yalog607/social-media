package com.aloute.repository.sticker;

import com.aloute.model.sticker.UserStickerPack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserStickerPackRepository extends JpaRepository<UserStickerPack, UUID> {
    List<UserStickerPack> findByUserIdOrderByInstalledAtAsc(UUID userId);
    Optional<UserStickerPack> findByUserIdAndPackId(UUID userId, String packId);
    boolean existsByUserIdAndPackId(UUID userId, String packId);
    void deleteByUserIdAndPackId(UUID userId, String packId);
}

