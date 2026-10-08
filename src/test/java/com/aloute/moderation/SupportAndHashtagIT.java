package com.aloute.moderation;

import com.aloute.common.RateLimitExceededException;
import com.aloute.post.InvalidPostException;
import com.aloute.post.PostService;
import com.aloute.search.SearchService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.Role;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hỗ trợ người dùng, hashtag bị cấm và thống kê của Manager. */
class SupportAndHashtagIT extends IntegrationTest {

    @Autowired SupportService support;
    @Autowired BannedHashtags hashtags;
    @Autowired PostService posts;
    @Autowired SearchService search;
    @Autowired ManageStats stats;
    @Autowired com.aloute.notification.NotificationService notifications;

    @Test
    void ticketIsAnsweredOnceAndTheUserSeesTheReply() {
        User user = createUser();
        User manager = createUser(Role.MANAGER);
        support.submit(user.getId(), "Không đăng nhập được", "Mình quên mật khẩu");

        SupportService.Ticket ticket = support.mine(user.getId()).get(0);
        assertThat(ticket.open()).isTrue();
        assertThat(support.open()).extracting(SupportService.Ticket::id).contains(ticket.id());

        support.reply(manager.getId(), ticket.id(), "Bạn dùng chức năng Quên mật khẩu nhé");

        SupportService.Ticket answered = support.mine(user.getId()).get(0);
        assertThat(answered.open()).isFalse();
        assertThat(answered.reply()).contains("Quên mật khẩu");
        assertThat(notifications.listRecent(user.getId()).get(0).text())
                .isEqualTo("Yêu cầu hỗ trợ của bạn đã có phản hồi mới.");
        assertThatThrownBy(() -> support.reply(manager.getId(), ticket.id(), "lần hai"))
                .isInstanceOf(InvalidModerationException.class);
    }

    @Test
    void ticketValidationAndDailyLimit() {
        User user = createUser();

        assertThatThrownBy(() -> support.submit(user.getId(), " ", "x")).isInstanceOf(InvalidModerationException.class);
        assertThatThrownBy(() -> support.submit(user.getId(), "x", "y".repeat(SupportService.MAX_BODY + 1)))
                .isInstanceOf(InvalidModerationException.class);
        User spammer = createUser();
        for (int i = 0; i < 5; i++) {
            support.submit(spammer.getId(), "Tiêu đề " + i, "Nội dung");
        }
        assertThatThrownBy(() -> support.submit(spammer.getId(), "thêm", "nữa")).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void bannedHashtagBlocksNewPostsAndCaptionsButNotOthers() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        String tag = "cam" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        hashtags.ban(manager.getId(), "#" + tag.toUpperCase());

        assertThat(hashtags.list()).contains(tag);
        assertThatThrownBy(() -> posts.create(author.getId(), "nội dung #" + tag, Visibility.PUBLIC, List.of(), null))
                .isInstanceOf(InvalidPostException.class).hasMessageContaining(tag);
        assertThat(posts.create(author.getId(), "bài ổn #khac", Visibility.PUBLIC, List.of(), null)).isNotNull();

        hashtags.unban(manager.getId(), tag);
        assertThat(posts.create(author.getId(), "giờ ổn #" + tag, Visibility.PUBLIC, List.of(), null)).isNotNull();
        assertThatThrownBy(() -> hashtags.ban(manager.getId(), "không hợp lệ !!")).isInstanceOf(InvalidModerationException.class);
    }

    @Test
    void bannedHashtagIsRemovedFromTrending() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        String tag = "hot" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        posts.create(author.getId(), "bài #" + tag, Visibility.PUBLIC, List.of(), null);
        assertThat(search.trending()).anyMatch(h -> h.tag().equals(tag));

        hashtags.ban(manager.getId(), tag);

        assertThat(search.trending()).noneMatch(h -> h.tag().equals(tag));
    }

    @Test
    void statsAndPagesWork() throws Exception {
        User manager = createUser(Role.MANAGER);
        User user = createUser();
        assertThat(stats.overview()).containsKeys("Báo cáo đang chờ", "Phiếu hỗ trợ đang chờ");

        mvc.perform(post("/support").param("subject", "Chào").param("body", "Cần giúp").with(csrf()).with(asUser(user)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/support").with(asUser(user))).andExpect(status().isOk()).andExpect(content().string(containsString("Cần giúp")));
        for (String path : List.of("/manage/support", "/manage/hashtags", "/manage/stats")) {
            mvc.perform(get(path).with(asUser(manager))).andExpect(status().isOk());
            mvc.perform(get(path).with(asUser(user))).andExpect(status().isForbidden());
        }
    }
}
