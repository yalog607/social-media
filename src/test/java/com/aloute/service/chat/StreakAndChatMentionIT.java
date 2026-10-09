package com.aloute.service.chat;

import com.aloute.dto.chat.MessageView;
import com.aloute.dto.chat.Streak;
import com.aloute.model.chat.Conversation;
import com.aloute.service.chat.ChatService;
import com.aloute.service.chat.MessageService;
import com.aloute.service.chat.StreakService;

import com.aloute.service.notification.NotificationService;
import com.aloute.service.social.FriendService;
import com.aloute.support.IntegrationTest;
import com.aloute.support.MutableClock;
import com.aloute.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chuỗi nhắn tin hằng ngày và nhắc tên (@) trong nhóm chat. */
class StreakAndChatMentionIT extends IntegrationTest {

    @Autowired ChatService chats;
    @Autowired MessageService messages;
    @Autowired StreakService streaks;
    @Autowired FriendService friends;
    @Autowired NotificationService notifications;
    @Autowired MutableClock clock;

    @BeforeEach
    void middayInVietnam() {
        // Đặt đồng hồ về 12:00 giờ Việt Nam để cộng đúng 24 giờ luôn sang ngày kế tiếp, không dính ranh giới nửa đêm
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), StreakService.ZONE);
        long hours = (12 - now.getHour() + 24) % 24;
        clock.advance(Duration.ofHours(hours).minusMinutes(now.getMinute()));
    }

    private void bothMessage(Conversation c, User a, User b) {
        messages.send(a.getId(), c.getId(), "chào", null);
        messages.send(b.getId(), c.getId(), "chào lại", null);
    }

    private void nextDay() {
        clock.advance(Duration.ofDays(1));
    }

    @Test
    void streakCountsConsecutiveDaysWhereBothSpoke() {
        User a = createUser();
        User b = createUser();
        Conversation c = chats.startDirect(a.getId(), b.getId());

        bothMessage(c, a, b);
        assertThat(streaks.forConversation(c.getId())).isEqualTo(new Streak(1, true));
        nextDay();
        assertThat(streaks.forConversation(c.getId())).as("hôm nay chưa nhắn: vẫn giữ nhưng có nguy cơ mất").isEqualTo(new Streak(1, false));
        assertThat(streaks.forConversation(c.getId()).atRisk()).isTrue();
        bothMessage(c, a, b);
        nextDay();
        bothMessage(c, a, b);

        assertThat(streaks.forConversation(c.getId())).isEqualTo(new Streak(3, true));
        assertThat(chats.listFor(a.getId())).filteredOn(s -> s.id().equals(c.getId())).singleElement()
                .satisfies(s -> assertThat(s.streak().days()).isEqualTo(3));
    }

    @Test
    void oneSidedMessagesDoNotCountAndAMissedDayBreaksTheStreak() {
        User a = createUser();
        User b = createUser();
        Conversation c = chats.startDirect(a.getId(), b.getId());

        messages.send(a.getId(), c.getId(), "chỉ mình mình nhắn", null);
        assertThat(streaks.forConversation(c.getId()).active()).isFalse();

        bothMessage(c, a, b);
        nextDay();
        nextDay();
        assertThat(streaks.forConversation(c.getId())).as("bỏ lỡ một ngày: mất chuỗi").isEqualTo(Streak.NONE);
        bothMessage(c, a, b);
        assertThat(streaks.forConversation(c.getId())).isEqualTo(new Streak(1, true));
    }

    @Test
    void computeHandlesEdgeCases() {
        LocalDate today = LocalDate.of(2026, 10, 10);

        assertThat(StreakService.compute(List.of(), today)).isEqualTo(Streak.NONE);
        assertThat(StreakService.compute(List.of(today.minusDays(2)), today)).isEqualTo(Streak.NONE);
        assertThat(StreakService.compute(List.of(today.minusDays(1), today.minusDays(2), today.minusDays(4)), today))
                .isEqualTo(new Streak(2, false));
    }

    @Test
    void groupChatsHaveNoStreak() {
        User a = createUser();
        User b = createUser();
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
        Conversation group = chats.createGroup(a.getId(), "Nhóm", List.of(b.getId()));
        bothMessage(group, a, b);

        assertThat(chats.listFor(a.getId())).filteredOn(s -> s.id().equals(group.getId())).singleElement()
                .satisfies(s -> assertThat(s.streak().active()).isFalse());
    }

    @Test
    void mentioningAGroupMemberNotifiesOnlyMembers() throws Exception {
        User a = createUser();
        User b = createUser();
        User outsider = createUser();
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
        Conversation group = chats.createGroup(a.getId(), "Hội bạn", List.of(b.getId()));

        MessageView view = messages.send(a.getId(), group.getId(), "ê @" + b.getUsername() + " và @" + outsider.getUsername(), null);

        assertThat(view.contentHtml()).contains("class=\"mention\"");
        assertThat(notifications.listRecent(b.getId())).filteredOn(n -> n.text().contains("nhắc đến bạn trong nhóm Hội bạn")).hasSize(1)
                .allSatisfy(n -> assertThat(n.conversationId()).isEqualTo(group.getId()));
        assertThat(notifications.listRecent(outsider.getId())).isEmpty();
        assertThat(notifications.listRecent(a.getId())).noneMatch(n -> n.text().contains("nhắc đến bạn"));

        mvc.perform(get("/api/conversations/" + group.getId() + "/members").with(asUser(a)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(b.getUsername())));
        mvc.perform(get("/api/conversations/" + group.getId() + "/members").with(asUser(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/notifications").with(asUser(b))).andExpect(status().isOk())
                .andExpect(content().string(containsString("/messages/" + group.getId())));
    }
}
