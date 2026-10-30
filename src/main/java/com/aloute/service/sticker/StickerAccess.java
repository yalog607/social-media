package com.aloute.service.sticker;

import com.aloute.exception.chat.ChatActionException;
import com.aloute.model.sticker.Sticker;
import com.aloute.repository.sticker.StickerRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** The single entitlement rule used by catalog, favorites and message sends. */
@Service
public class StickerAccess {
    private final JdbcTemplate jdbc;
    private final StickerRepository stickers;
    private final Clock clock;
    public StickerAccess(JdbcTemplate jdbc, StickerRepository stickers, Clock clock) {
        this.jdbc = jdbc; this.stickers = stickers; this.clock = clock;
    }
    public void lock(String packId) {
        if (jdbc.queryForList("select id from sticker_packs where id = ? for update", String.class, packId).isEmpty())
            throw new ChatActionException("Không tìm thấy bộ nhãn dán.");
    }
    private void enrollFree(UUID user, String pack) {
        jdbc.update("insert into user_sticker_packs (id,user_id,pack_id,installed_at,grant_source) "
                        + "select ?,?,id,?,'FREE' from sticker_packs where id=? and access_type='FREE' and status='PUBLISHED' "
                        + "on conflict (user_id,pack_id) do nothing",
                UUID.randomUUID(), user, Timestamp.from(clock.instant()), pack);
    }
    @Transactional
    public void collectPublishedFree(UUID user) {
        // Stable lock order also serializes retirement with first acquisition.
        List<String> ids = jdbc.queryForList("select id from sticker_packs where access_type='FREE' and status='PUBLISHED' order by id for update", String.class);
        for (String id : ids) enrollFree(user, id);
    }
    public boolean owns(UUID user, String pack) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from user_sticker_packs u join sticker_packs p on p.id=u.pack_id "
                + "where u.user_id=? and u.pack_id=? and u.revoked_at is null and p.status in ('PUBLISHED','RETIRED'))", Boolean.class, user, pack));
    }
    @Transactional
    public void requirePack(UUID user, String pack) {
        lock(pack); enrollFree(user, pack);
        if (!owns(user, pack)) throw new ChatActionException("Bạn chưa có quyền sử dụng bộ nhãn dán này.");
    }
    @Transactional
    public Sticker requireUsable(UUID user, String stickerId) {
        List<String> ids = jdbc.queryForList("select pack_id from stickers where id=? and removed=false and render_type in ('ATLAS','IMAGE')", String.class, stickerId);
        if (ids.isEmpty()) throw new ChatActionException("Không tìm thấy nhãn dán.");
        requirePack(user, ids.get(0));
        return stickers.findById(stickerId).filter(s -> !s.isRemoved()).orElseThrow(() -> new ChatActionException("Không tìm thấy nhãn dán."));
    }
}
