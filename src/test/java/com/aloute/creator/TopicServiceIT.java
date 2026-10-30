package com.aloute.creator;

import com.aloute.dto.category.CategoryView;
import com.aloute.exception.creator.InvalidTopicException;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.service.category.CategoryService;
import com.aloute.service.creator.TopicService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chủ đề (creator_topics) trên trang hồ sơ: thay toàn bộ danh sách, giới hạn 5, chỉ danh mục còn dùng được. */
class TopicServiceIT extends IntegrationTest {

    @Autowired TopicService topics;
    @Autowired CategoryService categories;
    @Autowired JdbcTemplate jdbc;

    private UUID newCategory(String name) {
        return categories.create(createUser(Role.MANAGER).getId(), name).id();
    }

    @Test
    void replaceOverwritesWholeSetInOrder() {
        User creator = createUser(Role.CREATOR);
        UUID a = newCategory("A" + UUID.randomUUID());
        UUID b = newCategory("B" + UUID.randomUUID());

        topics.replace(creator.getId(), List.of(a, b));
        assertThat(topics.topicIdsFor(creator.getId())).containsExactly(a, b);

        topics.replace(creator.getId(), List.of(b));
        assertThat(topics.topicIdsFor(creator.getId())).containsExactly(b);

        topics.replace(creator.getId(), List.of());
        assertThat(topics.topicIdsFor(creator.getId())).isEmpty();
    }

    @Test
    void onlyCreatorCanSetTopics() {
        User user = createUser();
        UUID a = newCategory("A" + UUID.randomUUID());
        assertThatThrownBy(() -> topics.replace(user.getId(), List.of(a))).isInstanceOf(InvalidTopicException.class);
    }

    @Test
    void rejectsMoreThanMaxTopics() {
        User creator = createUser(Role.CREATOR);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i <= TopicService.MAX_TOPICS; i++) {
            ids.add(newCategory("C" + i + UUID.randomUUID()));
        }
        assertThatThrownBy(() -> topics.replace(creator.getId(), ids)).isInstanceOf(InvalidTopicException.class);
    }

    @Test
    void rejectsInactiveOrUnknownCategory() {
        User creator = createUser(Role.CREATOR);
        User manager = createUser(Role.MANAGER);
        CategoryView inactive = categories.create(manager.getId(), "D" + UUID.randomUUID());
        categories.setActive(manager.getId(), inactive.id(), false);

        assertThatThrownBy(() -> topics.replace(creator.getId(), List.of(inactive.id())))
                .isInstanceOf(InvalidTopicException.class);
        assertThatThrownBy(() -> topics.replace(creator.getId(), List.of(UUID.randomUUID())))
                .isInstanceOf(InvalidTopicException.class);
    }

    @Test
    void deletingCategoryCascadesTopic() {
        User creator = createUser(Role.CREATOR);
        UUID a = newCategory("E" + UUID.randomUUID());
        topics.replace(creator.getId(), List.of(a));

        jdbc.update("delete from categories where id = ?", a);

        assertThat(topics.topicIdsFor(creator.getId())).isEmpty();
    }

    @Test
    void onlyCreatorRoleCanCallHttpRoute() throws Exception {
        User user = createUser();
        User creator = createUser(Role.CREATOR);
        UUID a = newCategory("F" + UUID.randomUUID());

        mvc.perform(post("/creator/topics").with(asUser(user)).with(csrf()).param("categoryIds", a.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/creator/topics").with(asUser(creator)).with(csrf()).param("categoryIds", a.toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(topics.topicIdsFor(creator.getId())).containsExactly(a);
    }
}
