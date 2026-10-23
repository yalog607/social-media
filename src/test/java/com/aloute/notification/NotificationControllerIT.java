package com.aloute.notification;

import com.aloute.model.notification.Notification;
import com.aloute.model.notification.NotificationType;
import com.aloute.repository.notification.NotificationRepository;
import com.aloute.service.notification.NotificationService;

import com.aloute.service.social.FollowService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
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

    @Autowired com.aloute.service.post.PostService posts;
    @Autowired NotificationRepository notificationRepository;

    @Test
    void emptyStateShowsWhenThereAreNoNotifications() throws Exception {
        User me = createUser();

        mvc.perform(get("/notifications").with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có thông báo nào")));
    }

    @Test
    void readAndRedirectSafelyHandlesDeletedPost() throws Exception {
        User me = createUser();
        User author = createUser();
        var post = posts.create(author.getId(), "Bài viết này sẽ bị xóa", com.aloute.model.user.Visibility.PUBLIC, null, null, null, null, null, null);
        posts.delete(author.getId(), post.getId());

        Notification n = new Notification();
        n.setRecipient(me);
        n.setActor(author);
        n.setType(NotificationType.POST_REACTION);
        n.setPost(post);
        n = notificationRepository.save(n);

        mvc.perform(get("/notifications/" + n.getId() + "/read")
                        .param("postId", post.getId().toString())
                        .with(asUser(me)))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/notifications"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"));

        assertThat(notifications.unreadCount(me.getId())).isZero();
    }
}
