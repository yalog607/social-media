package com.aloute.notification;

import com.aloute.social.FollowService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phần 3b: trang thông báo qua HTTP, và số chưa đọc hiện ở khung ứng dụng. */
class NotificationControllerIT extends IntegrationTest {

    @Autowired FollowService follows;
    @Autowired NotificationService notifications;

    @Test
    void unreadBadgeShowsUpOnAnyAppPageThenPageMarksItRead() throws Exception {
        User me = createUser();
        follows.follow(createUser().getId(), me.getId());

        mvc.perform(get("/").with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nav-badge")));

        mvc.perform(get("/notifications").with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("theo dõi")));

        assertThat(notifications.unreadCount(me.getId())).as("mở trang thông báo tự đánh dấu đã đọc").isZero();
    }

    @Test
    void emptyStateShowsWhenThereAreNoNotifications() throws Exception {
        User me = createUser();

        mvc.perform(get("/notifications").with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có thông báo nào")));
    }
}
