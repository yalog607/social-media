package com.aloute.service.sticker;

import com.aloute.dto.sticker.StickerPackView;
import com.aloute.exception.chat.ChatActionException;
import com.aloute.repository.sticker.StickerPackRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class LocalStickerLibrary {
    private final StickerPackRepository packs;
    private final StickerService stickers;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final StickerAccess access;
    public LocalStickerLibrary(StickerPackRepository packs, StickerService stickers, JdbcTemplate jdbc, Clock clock, StickerAccess access) {
        this.packs = packs; this.stickers = stickers; this.jdbc = jdbc; this.clock = clock;
        this.access = access;
    }
    public record Library(List<StickerPackView> packs, List<String> recent, List<String> favorites) {}

    @Transactional
    public Library library(UUID user) {
        access.collectPublishedFree(user);
        List<String> owned = jdbc.queryForList("select u.pack_id from user_sticker_packs u join sticker_packs p on p.id=u.pack_id where u.user_id=? and u.revoked_at is null and p.status in ('PUBLISHED','RETIRED')", String.class, user);
        var views = packs.findAllByOrderBySortOrderAsc().stream().filter(p -> owned.contains(p.getId()))
                .map(p -> stickers.toPackView(p, true, true)).toList();
        var visible = views.stream().flatMap(p -> p.stickers().stream()).filter(s -> s.visibleArtwork()).map(s -> s.id()).collect(java.util.stream.Collectors.toSet());
        return new Library(views,
                jdbc.queryForList("select sticker_id from user_sticker_recent where user_id = ? order by used_at desc, sticker_id", String.class, user).stream().filter(visible::contains).limit(24).toList(),
                jdbc.queryForList("select sticker_id from user_sticker_favorites where user_id = ? order by added_at desc, sticker_id", String.class, user).stream().filter(visible::contains).toList());
    }

    @Transactional
    public void favorite(UUID user, String id, boolean enabled) {
        if (enabled) access.requireUsable(user, id);
        if (enabled) jdbc.update("insert into user_sticker_favorites (user_id, sticker_id, added_at) values (?, ?, ?) on conflict do nothing", user, id, Timestamp.from(clock.instant()));
        else jdbc.update("delete from user_sticker_favorites where user_id = ? and sticker_id = ?", user, id);
    }

    @Transactional
    public void recordUse(UUID user, String id) {
        jdbc.update("insert into user_sticker_recent (user_id, sticker_id, used_at) values (?, ?, ?) on conflict (user_id, sticker_id) do update set used_at = excluded.used_at", user, id, Timestamp.from(clock.instant()));
    }

    @Transactional
    public StickerPackView ownedPack(UUID user, String id) {
        access.requirePack(user, id);
        return stickers.toPackView(packs.findById(id).orElseThrow(), true, true);
    }
}
