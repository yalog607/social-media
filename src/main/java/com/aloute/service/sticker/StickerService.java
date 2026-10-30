package com.aloute.service.sticker;

import com.aloute.dto.sticker.StickerPackView;
import com.aloute.dto.sticker.StickerView;
import com.aloute.model.sticker.Sticker;
import com.aloute.model.sticker.StickerPack;
import com.aloute.model.sticker.UserStickerPack;
import com.aloute.model.user.User;
import com.aloute.repository.sticker.StickerPackRepository;
import com.aloute.repository.sticker.StickerRepository;
import com.aloute.repository.sticker.UserStickerPackRepository;
import com.aloute.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class StickerService {

    private final StickerPackRepository packRepository;
    private final StickerRepository stickerRepository;
    private final UserStickerPackRepository userPackRepository;
    private final UserRepository userRepository;

    public StickerService(StickerPackRepository packRepository,
                          StickerRepository stickerRepository,
                          UserStickerPackRepository userPackRepository,
                          UserRepository userRepository) {
        this.packRepository = packRepository;
        this.stickerRepository = stickerRepository;
        this.userPackRepository = userPackRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void ensureDefaultPacksInstalled(UUID userId) {
        List<UserStickerPack> existing = userPackRepository.findByUserIdOrderByInstalledAtAsc(userId);
        if (existing.isEmpty()) {
            User user = userRepository.getReferenceById(userId);
            List<StickerPack> defaults = packRepository.findByIsDefaultTrueOrderBySortOrderAsc();
            for (StickerPack pack : defaults) {
                userPackRepository.save(new UserStickerPack(user, pack));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<StickerPackView> getMyPacks(UUID userId) {
        List<UserStickerPack> userPacks = userPackRepository.findByUserIdOrderByInstalledAtAsc(userId);
        if (userPacks.isEmpty()) {
            // Lazy enroll default packs
            List<StickerPack> defaults = packRepository.findByIsDefaultTrueOrderBySortOrderAsc();
            return defaults.stream().map(p -> toPackView(p, true, true)).toList();
        }
        return userPacks.stream()
                .map(up -> toPackView(up.getPack(), true, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StickerPackView> getAllStorePacks(UUID userId) {
        List<StickerPack> allPacks = packRepository.findAllByOrderBySortOrderAsc();
        List<UserStickerPack> userPacks = userPackRepository.findByUserIdOrderByInstalledAtAsc(userId);
        Set<String> installedIds = new HashSet<>();
        if (userPacks.isEmpty()) {
            allPacks.stream().filter(StickerPack::isDefault).forEach(p -> installedIds.add(p.getId()));
        } else {
            userPacks.forEach(up -> installedIds.add(up.getPack().getId()));
        }

        return allPacks.stream()
                .map(p -> toPackView(p, installedIds.contains(p.getId()), true))
                .toList();
    }

    @Transactional
    public boolean installPack(UUID userId, String packId) {
        if (userPackRepository.existsByUserIdAndPackId(userId, packId)) {
            return true;
        }
        StickerPack pack = packRepository.findById(packId).orElse(null);
        if (pack == null) return false;

        User user = userRepository.getReferenceById(userId);
        userPackRepository.save(new UserStickerPack(user, pack));
        return true;
    }

    @Transactional
    public boolean uninstallPack(UUID userId, String packId) {
        if (!userPackRepository.existsByUserIdAndPackId(userId, packId)) {
            return true;
        }
        userPackRepository.deleteByUserIdAndPackId(userId, packId);
        return true;
    }

    @Transactional(readOnly = true)
    public Sticker getSticker(String stickerId) {
        return stickerRepository.findById(stickerId).orElse(null);
    }

    public StickerView toStickerView(Sticker s) {
        if (s == null) return null;
        return new StickerView(
                s.getId(),
                s.getPack() != null ? s.getPack().getId() : null,
                s.getCode(),
                s.getName(),
                s.getImageUrl(),
                s.getKeywords(),
                s.getRenderType().name(),
                s.getSortOrder()
        );
    }

    public StickerPackView toPackView(StickerPack pack, boolean installed, boolean includeStickers) {
        List<StickerView> stickerViews = new ArrayList<>();
        if (includeStickers) {
            List<Sticker> stickers = stickerRepository.findByPackIdOrderBySortOrderAsc(pack.getId());
            stickerViews = stickers.stream().map(this::toStickerView).toList();
        }
        return new StickerPackView(
                pack.getId(),
                pack.getName(),
                pack.getDescription(),
                pack.getIconUrl(),
                pack.getAuthor(),
                pack.getTotalStickers(),
                installed,
                stickerViews,
                pack.getAccessType().name(),
                pack.getStatus().name()
        );
    }
}

