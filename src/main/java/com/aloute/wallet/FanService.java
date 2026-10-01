package com.aloute.wallet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Huy hiệu fan, tính thẳng từ sổ cái donate nên luôn khớp số liệu thật và không cần bảng riêng cần đồng bộ.
 * Chỉ tính donate TRỰC TIẾP cho Creator (không tính tiền mở khóa bài).
 */
@Service
public class FanService {

    private static final int TOP_FANS = 50;

    private final JdbcTemplate jdbc;

    public FanService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Huy hiệu của các {@code fanIds} đối với {@code creatorId} (chỉ gồm người đã đạt ngưỡng), một truy vấn cho cả lô. */
    @Transactional(readOnly = true)
    public Map<UUID, FanBadge> badgesFor(UUID creatorId, Collection<UUID> fanIds) {
        Map<UUID, FanBadge> badges = new HashMap<>();
        if (fanIds.isEmpty()) {
            return badges;
        }
        String marks = String.join(",", java.util.Collections.nCopies(fanIds.size(), "?"));
        Object[] args = new Object[fanIds.size() + 1];
        args[0] = creatorId;
        int i = 1;
        for (UUID id : fanIds) {
            args[i++] = id;
        }
        jdbc.query("""
                select counterparty_id, sum(amount) from wallet_transactions
                where user_id = ? and type = 'DONATE_RECEIVED' and counterparty_id in (""" + marks + ") group by counterparty_id",
                rs -> {
                    UUID fanId = rs.getObject(1, UUID.class);
                    FanBadge.forTotal(rs.getLong(2)).ifPresent(badge -> badges.put(fanId, badge));
                }, args);
        return badges;
    }

    /** Những người đã tặng Xu cho {@code creatorId}, nhiều nhất trước. */
    @Transactional(readOnly = true)
    public List<FanView> topFans(UUID creatorId) {
        return jdbc.query("""
                select t.counterparty_id, u.username, p.display_name, sum(t.amount) as total
                from wallet_transactions t
                join users u on u.id = t.counterparty_id
                join profiles p on p.user_id = t.counterparty_id
                where t.user_id = ? and t.type = 'DONATE_RECEIVED'
                group by t.counterparty_id, u.username, p.display_name
                order by total desc, p.display_name limit ?""",
                (rs, i) -> new FanView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getLong(4),
                        FanBadge.forTotal(rs.getLong(4)).orElse(null)),
                creatorId, TOP_FANS);
    }
}
