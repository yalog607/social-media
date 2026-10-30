package com.aloute.service.chat;

import com.aloute.dto.chat.Streak;
import com.aloute.model.chat.Conversation;
import com.aloute.repository.chat.ConversationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * Tính chuỗi nhắn tin trực tiếp từ bảng tin nhắn.
 */
@Service
public class StreakService {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int LOOKBACK_DAYS = 400;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ConversationRepository conversations;

    public StreakService(JdbcTemplate jdbc, Clock clock, ConversationRepository conversations) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.conversations = conversations;
    }

    /** Chuỗi của nhiều hội thoại 1-1 cùng lúc (một truy vấn); hội thoại không có chuỗi thì không có trong kết quả. */
    @Transactional(readOnly = true)
    public Map<UUID, Streak> forConversations(Collection<UUID> directConversationIds) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        Map<UUID, Streak> result = new HashMap<>();
        if (directConversationIds.isEmpty()) {
            return result;
        }
        String marks = String.join(",", Collections.nCopies(directConversationIds.size(), "?"));
        List<Object> args = new ArrayList<>(directConversationIds);
        args.add(java.sql.Date.valueOf(today.minusDays(LOOKBACK_DAYS)));
        Map<UUID, List<LocalDate>> daysByConversation = new HashMap<>();
        jdbc.query("""
                select messages.conversation_id, (messages.created_at at time zone 'Asia/Ho_Chi_Minh')::date as d
                from messages
                join conversations c on c.id = messages.conversation_id
                where c.type = 'DIRECT'
                  and messages.conversation_id in (""" + marks + """
                ) and (messages.created_at at time zone 'Asia/Ho_Chi_Minh')::date >= ?
                group by messages.conversation_id, d
                having count(distinct messages.sender_id) >= (select count(*) from conversation_members cm where cm.conversation_id = messages.conversation_id)
                order by messages.conversation_id, d desc""", (RowCallbackHandler) rs -> {
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

    /**
     * Lấy chuỗi nhắn tin giữa một người dùng và danh sách bạn bè.
     * Trả về Map<friendId, Streak>. Chỉ bạn bè có chuỗi active (days > 0) mới có trong map.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Streak> forUserAndFriends(UUID userId, Collection<UUID> friendIds) {
        if (userId == null || friendIds == null || friendIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, UUID> keyToFriend = new HashMap<>();
        for (UUID friendId : friendIds) {
            if (friendId != null && !friendId.equals(userId)) {
                String key = ChatService.directKey(userId, friendId);
                keyToFriend.put(key, friendId);
            }
        }
        if (keyToFriend.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Conversation> convs = conversations.findByDirectKeyIn(keyToFriend.keySet());
        if (convs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, UUID> convToFriend = new HashMap<>();
        for (Conversation c : convs) {
            UUID friendId = keyToFriend.get(c.getDirectKey());
            if (friendId != null) {
                convToFriend.put(c.getId(), friendId);
            }
        }
        Map<UUID, Streak> convStreaks = forConversations(convToFriend.keySet());
        Map<UUID, Streak> result = new HashMap<>();
        convStreaks.forEach((convId, streak) -> {
            UUID friendId = convToFriend.get(convId);
            if (friendId != null && streak.active()) {
                result.put(friendId, streak);
            }
        });
        return result;
    }

    /**
     * Lấy chuỗi nhắn tin trực tiếp giữa hai người dùng cụ thể.
     */
    @Transactional(readOnly = true)
    public Streak forDirect(UUID userA, UUID userB) {
        if (userA == null || userB == null || userA.equals(userB)) {
            return Streak.NONE;
        }
        String key = ChatService.directKey(userA, userB);
        return conversations.findByDirectKey(key)
                .map(c -> forConversation(c.getId()))
                .orElse(Streak.NONE);
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
