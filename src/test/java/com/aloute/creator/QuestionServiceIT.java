package com.aloute.creator;

import com.aloute.dto.notification.NotificationView;
import com.aloute.dto.post.PostView;
import com.aloute.exception.post.InvalidPostException;
import com.aloute.model.notification.NotificationType;
import com.aloute.model.post.Post;
import com.aloute.model.post.PostKind;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.service.comment.CommentService;
import com.aloute.service.creator.QuestionService;
import com.aloute.service.notification.NotificationService;
import com.aloute.service.post.PostService;
import com.aloute.service.post.PostViewAssembler;
import com.aloute.service.social.BlockService;
import com.aloute.service.social.FollowService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hỏi đáp công khai của Creator: tạo câu hỏi, thông báo người theo dõi, trả lời bằng bình luận, quyền và giữ công khai. */
class QuestionServiceIT extends IntegrationTest {

    @Autowired QuestionService questions;
    @Autowired FollowService follows;
    @Autowired BlockService blocks;
    @Autowired NotificationService notifications;
    @Autowired PostViewAssembler assembler;
    @Autowired PostService postService;
    @Autowired CommentService comments;
    @Autowired JdbcTemplate jdbc;

    private List<NotificationView> questionNotifications(User user) {
        return notifications.listRecent(user.getId()).stream()
                .filter(n -> n.type() == NotificationType.CREATOR_QUESTION).toList();
    }

    @Test
    void creatorAsksAndOnlyFollowersAreNotified() {
        User creator = createUser(Role.CREATOR);
        User follower = createUser();
        User stranger = createUser();
        User blockedFollower = createUser();
        follows.follow(follower.getId(), creator.getId());
        follows.follow(blockedFollower.getId(), creator.getId());
        blocks.block(creator.getId(), blockedFollower.getId());
        jdbc.update("insert into follows (id, follower_id, followee_id) values (gen_random_uuid(), ?, ?)",
                blockedFollower.getId(), creator.getId());

        Post question = questions.create(creator.getId(), "Bạn học lập trình bằng cách nào?");

        assertThat(question.getPostKind()).isEqualTo(PostKind.QUESTION);
        assertThat(question.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(questionNotifications(follower)).singleElement().satisfies(n -> {
            assertThat(n.text()).contains("câu hỏi");
            assertThat(n.postId()).isEqualTo(question.getId());
        });
        assertThat(questionNotifications(stranger)).isEmpty();
        assertThat(questionNotifications(blockedFollower)).isEmpty();
        assertThat(questionNotifications(creator)).isEmpty();
        PostView view = assembler.assemble(List.of(question), follower.getId()).get(0);
        assertThat(view.isQuestion()).isTrue();
    }

    @Test
    void answersAreCommentsAndSendNoFollowerNotification() {
        User creator = createUser(Role.CREATOR);
        User follower = createUser();
        User other = createUser();
        follows.follow(follower.getId(), creator.getId());
        Post question = questions.create(creator.getId(), "Câu hỏi?");

        comments.create(other.getId(), question.getId(), null, "Mình học qua dự án nhỏ", null, null);

        assertThat(questionNotifications(follower)).as("không thêm thông báo cho câu trả lời").hasSize(1);
        assertThat(assembler.assemble(List.of(question), other.getId()).get(0).commentCount()).isEqualTo(1);
    }

    @Test
    void validationAndRoles() {
        User user = createUser();
        User creator = createUser(Role.CREATOR);

        assertThatThrownBy(() -> questions.create(user.getId(), "Hỏi?")).isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> questions.create(creator.getId(), "   ")).isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> questions.create(creator.getId(), "x".repeat(2001))).isInstanceOf(InvalidPostException.class);
    }

    @Test
    void questionStaysPublicWhenEdited() {
        User creator = createUser(Role.CREATOR);
        Post question = questions.create(creator.getId(), "Câu hỏi?");

        assertThatThrownBy(() -> postService.edit(creator.getId(), question.getId(), "Câu hỏi mới", Visibility.PRIVATE))
                .isInstanceOf(InvalidPostException.class);
    }

    @Test
    void httpRoutesEnforceRoles() throws Exception {
        User creator = createUser(Role.CREATOR);
        User user = createUser();

        mvc.perform(post("/creator/questions").with(asUser(user)).with(csrf()).param("question", "Q?"))
                .andExpect(status().isForbidden());
        String location = mvc.perform(post("/creator/questions").with(asUser(creator)).with(csrf()).param("question", "Q?"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/posts/");
        mvc.perform(post("/creator/questions").with(asUser(creator)).param("question", "Q?"))
                .andExpect(status().isForbidden());
    }
}
