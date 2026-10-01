package com.aloute.creator;

import com.aloute.notification.NotificationService;
import com.aloute.notification.NotificationView;
import com.aloute.social.BlockService;
import com.aloute.social.FollowService;
import com.aloute.support.IntegrationTest;
import com.aloute.support.MutableClock;
import com.aloute.user.Role;
import com.aloute.user.User;
import com.aloute.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhắn hàng loạt của Creator: đúng tập người nhận, bỏ người bị chặn, hạn mức theo ngày, hiển thị ở thông báo. */
class BroadcastServiceIT extends IntegrationTest {

    @Autowired BroadcastService broadcasts;
    @Autowired FollowService follows;
    @Autowired BlockService blocks;
    @Autowired WalletService wallet;
    @Autowired NotificationService notifications;
    @Autowired MutableClock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private List<NotificationView> broadcastsFor(User user) {
        return notifications.listRecent(user.getId()).stream().filter(n -> n.detail() != null).toList();
    }

    @Test
    void followersReceiveItAndOthersDoNot() {
        User creator = createUser(Role.CREATOR);
        User follower = createUser();
        User stranger = createUser();
        follows.follow(follower.getId(), creator.getId());

        int sent = broadcasts.send(creator.getId(), BroadcastAudience.FOLLOWERS, "Chào cả nhà", "Tối nay mình livestream nhé");

        assertThat(sent).isEqualTo(1);
        assertThat(broadcastsFor(follower)).singleElement().satisfies(n -> {
            assertThat(n.text()).contains("Chào cả nhà");
            assertThat(n.detail()).isEqualTo("Tối nay mình livestream nhé");
            assertThat(n.actor().id()).isEqualTo(creator.getId());
        });
        assertThat(broadcastsFor(stranger)).isEmpty();
        assertThat(broadcastsFor(creator)).as("Creator không tự nhận").isEmpty();
        assertThat(broadcasts.history(creator.getId())).singleElement()
                .satisfies(h -> assertThat(h.recipientCount()).isEqualTo(1));
    }

    @Test
    void onlyFansWithABadgeReceiveAFansBroadcast() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        User follower = createUser();
        User smallDonor = createUser();
        follows.follow(follower.getId(), creator.getId());
        wallet.donate(fan.getId(), creator.getId(), 20);
        wallet.donate(smallDonor.getId(), creator.getId(), 5);

        assertThat(broadcasts.send(creator.getId(), BroadcastAudience.FANS, "Dành cho fan", "Quà nhỏ")).isEqualTo(1);

        assertThat(broadcastsFor(fan)).hasSize(1);
        assertThat(broadcastsFor(follower)).isEmpty();
        assertThat(broadcastsFor(smallDonor)).isEmpty();
    }

    @Test
    void blockedUsersAreSkippedInBothDirections() {
        User creator = createUser(Role.CREATOR);
        User blockedByCreator = createUser();
        User blockingCreator = createUser();
        User ok = createUser();
        for (User u : List.of(blockedByCreator, blockingCreator, ok)) {
            follows.follow(u.getId(), creator.getId());
        }
        blocks.block(blockingCreator.getId(), creator.getId());
        // sau khi bị chặn, follow bị gỡ; tạo lại bằng dữ liệu trực tiếp để kiểm tra riêng bộ lọc chặn của lần gửi
        jdbc.update("insert into follows (id, follower_id, followee_id) values (gen_random_uuid(), ?, ?)",
                blockingCreator.getId(), creator.getId());
        blocks.block(creator.getId(), blockedByCreator.getId());
        jdbc.update("insert into follows (id, follower_id, followee_id) values (gen_random_uuid(), ?, ?)",
                blockedByCreator.getId(), creator.getId());

        int sent = broadcasts.send(creator.getId(), BroadcastAudience.FOLLOWERS, "Tin", "Nội dung");

        assertThat(sent).isEqualTo(1);
        assertThat(broadcastsFor(ok)).hasSize(1);
        assertThat(broadcastsFor(blockedByCreator)).isEmpty();
        assertThat(broadcastsFor(blockingCreator)).isEmpty();
    }

    @Test
    void validatesContentAudienceAndRole() {
        User creator = createUser(Role.CREATOR);
        User plain = createUser();
        follows.follow(createUser().getId(), creator.getId());

        assertThatThrownBy(() -> broadcasts.send(plain.getId(), BroadcastAudience.FOLLOWERS, "a", "b"))
                .isInstanceOf(InvalidBroadcastException.class);
        assertThatThrownBy(() -> broadcasts.send(creator.getId(), BroadcastAudience.FOLLOWERS, " ", "b"))
                .isInstanceOf(InvalidBroadcastException.class);
        assertThatThrownBy(() -> broadcasts.send(creator.getId(), BroadcastAudience.FOLLOWERS, "a", "x".repeat(BroadcastService.MAX_BODY + 1)))
                .isInstanceOf(InvalidBroadcastException.class);
        assertThatThrownBy(() -> broadcasts.send(creator.getId(), BroadcastAudience.FANS, "a", "b"))
                .hasMessageContaining("Chưa có ai");
    }

    @Test
    void limitedToThreePerDayAndResetsTheNextDay() {
        User creator = createUser(Role.CREATOR);
        follows.follow(createUser().getId(), creator.getId());
        for (int i = 0; i < BroadcastService.MAX_PER_DAY; i++) {
            broadcasts.send(creator.getId(), BroadcastAudience.FOLLOWERS, "Tin " + i, "Nội dung");
        }

        assertThatThrownBy(() -> broadcasts.send(creator.getId(), BroadcastAudience.FOLLOWERS, "Thêm", "Nội dung"))
                .isInstanceOf(InvalidBroadcastException.class).hasMessageContaining("3 lần");

        clock.advance(Duration.ofDays(1).plusMinutes(1));
        assertThat(broadcasts.send(creator.getId(), BroadcastAudience.FOLLOWERS, "Hôm sau", "Nội dung")).isEqualTo(1);
    }

    @Test
    void pagesWorkAndAreRestrictedToCreators() throws Exception {
        User creator = createUser(Role.CREATOR);
        User follower = createUser();
        follows.follow(follower.getId(), creator.getId());

        mvc.perform(post("/creator/broadcasts").param("audience", "FOLLOWERS").param("title", "Alo").param("body", "Có tin mới")
                        .with(csrf()).with(asUser(creator)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/creator/broadcasts").with(asUser(creator))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Alo")));
        mvc.perform(get("/notifications").with(asUser(follower))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Có tin mới")));
        mvc.perform(get("/creator/broadcasts").with(asUser(follower))).andExpect(status().isForbidden());
        mvc.perform(post("/creator/broadcasts").param("audience", "FOLLOWERS").param("title", "x").param("body", "y")
                        .with(csrf()).with(asUser(follower)))
                .andExpect(status().isForbidden());
    }
}
