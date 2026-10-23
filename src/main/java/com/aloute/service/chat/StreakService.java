package com.aloute.service.chat;

import com.aloute.dto.chat.Streak;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tính chuỗi nhắn tin trực tiếp từ bảng tin nhắn (không có bảng riêng nên không bao giờ lệch số liệu thật). Ngày được
 * tính theo giờ Việt Nam. Một ngày "đạt" khi trong ngày có tin của cả hai người; chuỗi là số ngày đạt liên tiếp tính
 * lùi từ hôm nay (hoặc từ hôm qua nếu hôm nay chưa đủ hai bên — chuỗi chỉ đứt khi hết ngày mà vẫn thiếu).
 */
@Service
public class StreakService {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int LOOKBACK_DAYS = 400;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public StreakService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Chuỗi của nhiều hội thoại 1-1 cùng lúc (một truy vấn); hội thoại không có chuỗi thì không có trong kết quả. */
    @Transactional(readOnly = true)
    public Map<UUID, Streak> forConversations(Collection<UUID> directConversationIds) {
        Map<UUID, Streak> result = new HashMap<>();
        if (directConversationIds.isEmpty()) {
            return result;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        String marks = String.join(",", Collections.nCopies(directConversationIds.size(), "?"));
        List<Object> args = new ArrayList<>(directConversationIds);
        args.add(Date.valueOf(today.minusDays(LOOKBACK_DAYS)));
        Map<UUID, List<LocalDate>> daysByConversation = new HashMap<>();
        jdbc.query("""
                select conversation_id, (created_at at time zone 'Asia/Ho_Chi_Minh')::date as d
                from messages
                where conversation_id in (""" + marks + """
                ) and (created_at at time zone 'Asia/Ho_Chi_Minh')::date >= ?
                group by conversation_id, d
                having count(distinct sender_id) >= (select count(*) from conversation_members cm where cm.conversation_id = messages.conversation_id)
                order by conversation_id, d desc""", rs -> {
            daysByConversation.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                    .add(rs.getDate(2).toLocalDate());
        }, args.toArray());
        daysByConversation.forEach((id, days) -> {
            Streak streak = compute(days, today);
            if (streak.active()) {
                result.put(id, streak);
            }
        });
        return result;
    }

    @Transactional(readOnly = true)
    public Streak forConversation(UUID conversationId) {
        return forConversations(List.of(conversationId)).getOrDefault(conversationId, Streak.NONE);
    }

    /** @param daysDescending các ngày đạt, mới nhất trước, không trùng */
    static Streak compute(List<LocalDate> daysDescending, LocalDate today) {
        if (daysDescending.isEmpty()) {
            return Streak.NONE;
        }
        boolean doneToday = daysDescending.get(0).equals(today);
        LocalDate expected = doneToday ? today : today.minusDays(1);
        if (!daysDescending.get(0).equals(expected)) {
            return Streak.NONE;
        }
        int count = 0;
        for (LocalDate day : daysDescending) {
            if (!day.equals(expected)) {
                break;
            }
            count++;
            expected = expected.minusDays(1);
        }
        return new Streak(count, doneToday);
    }
}
