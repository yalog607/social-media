package com.aloute.category;

import com.aloute.dto.category.CategoryView;
import com.aloute.exception.category.InvalidCategoryException;
import com.aloute.service.category.CategoryService;

import com.aloute.service.feed.FeedService;
import com.aloute.exception.post.InvalidPostException;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.dto.post.PostView;
import com.aloute.service.post.PostViewAssembler;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
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

/** Danh mục: Manager quản lý, người dùng chọn khi đăng/sửa bài, trang bài theo danh mục. */
class CategoryIT extends IntegrationTest {

    @Autowired CategoryService categories;
    @Autowired PostService posts;
    @Autowired PostViewAssembler assembler;
    @Autowired FeedService feed;

    private String unique() {
        return "Mục " + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID current(Post post, User author) {
        return posts.getVisible(post.getId(), author.getId()).getCategoryId();
    }

    private Post newPost(User author, UUID categoryId, Visibility visibility) {
        return posts.create(author.getId(), "bài trong mục", visibility, List.of(), null, List.of(), null, null, categoryId);
    }

    @Test
    void managerCreatesRenamesAndDisablesCategories() {
        User manager = createUser(Role.MANAGER);
        CategoryView c = categories.create(manager.getId(), "  Âm   nhạc " + UUID.randomUUID().toString().substring(0, 6));

        assertThat(c.slug()).startsWith("am-nhac-");
        assertThat(c.name()).startsWith("Âm nhạc");
        assertThat(categories.active()).extracting(CategoryView::id).contains(c.id());
        assertThatThrownBy(() -> categories.create(manager.getId(), c.name())).isInstanceOf(InvalidCategoryException.class);
        assertThatThrownBy(() -> categories.create(manager.getId(), " ")).isInstanceOf(InvalidCategoryException.class);
        assertThatThrownBy(() -> categories.create(manager.getId(), "x".repeat(CategoryService.MAX_NAME + 1)))
                .isInstanceOf(InvalidCategoryException.class);

        categories.rename(manager.getId(), c.id(), "Tên mới " + c.slug());
        assertThat(categories.bySlug(c.slug())).get().satisfies(v -> assertThat(v.name()).startsWith("Tên mới"));

        categories.setActive(manager.getId(), c.id(), false);
        assertThat(categories.active()).extracting(CategoryView::id).doesNotContain(c.id());
        assertThat(categories.all()).extracting(CategoryView::id).contains(c.id());
    }

    @Test
    void postsCarryTheCategoryAndOnlyActiveOnesCanBeChosen() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        CategoryView c = categories.create(manager.getId(), unique());
        Post post = newPost(author, c.id(), Visibility.PUBLIC);

        PostView view = assembler.assemble(List.of(post), author.getId()).get(0);
        assertThat(view.category()).isNotNull();
        assertThat(view.category().id()).isEqualTo(c.id());

        categories.setActive(manager.getId(), c.id(), false);
        assertThatThrownBy(() -> newPost(author, c.id(), Visibility.PUBLIC)).isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> newPost(author, UUID.randomUUID(), Visibility.PUBLIC)).isInstanceOf(InvalidPostException.class);
        assertThat(assembler.assemble(List.of(post), author.getId()).get(0).category()).as("bài cũ giữ nhãn").isNotNull();
    }

    @Test
    void categoryFeedShowsOnlyPublicPostsOfThatCategory() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        CategoryView c = categories.create(manager.getId(), unique());
        CategoryView other = categories.create(manager.getId(), unique());
        Post inCategory = newPost(author, c.id(), Visibility.PUBLIC);
        Post privatePost = newPost(author, c.id(), Visibility.PRIVATE);
        Post elsewhere = newPost(author, other.id(), Visibility.PUBLIC);

        List<UUID> ids = feed.byCategory(c.id(), createUser().getId(), null).posts().stream().map(PostView::id).toList();

        assertThat(ids).containsExactly(inCategory.getId());
        assertThat(ids).doesNotContain(privatePost.getId(), elsewhere.getId());
    }

    @Test
    void editingChangesOrClearsTheCategoryAndKeepsAnInactiveOne() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        CategoryView first = categories.create(manager.getId(), unique());
        CategoryView second = categories.create(manager.getId(), unique());
        Post post = newPost(author, first.id(), Visibility.PUBLIC);

        posts.edit(author.getId(), post.getId(), "đổi mục", null, null, second.id());
        assertThat(current(post, author)).isEqualTo(second.id());

        categories.setActive(manager.getId(), second.id(), false);
        posts.edit(author.getId(), post.getId(), "vẫn giữ mục đã ngưng", null, null, second.id());
        assertThat(current(post, author)).as("giữ nguyên danh mục cũ dù đã ngưng").isEqualTo(second.id());
        categories.setActive(manager.getId(), first.id(), false);
        assertThatThrownBy(() -> posts.edit(author.getId(), post.getId(), "x", null, null, first.id()))
                .as("không chuyển sang danh mục đã ngưng").isInstanceOf(InvalidPostException.class);

        posts.edit(author.getId(), post.getId(), "bỏ mục", null, null, null);
        assertThat(current(post, author)).isNull();
    }

    @Test
    void pagesAndHttpFlow() throws Exception {
        User manager = createUser(Role.MANAGER);
        User user = createUser();
        String name = unique();

        mvc.perform(post("/manage/categories").param("name", name).with(csrf()).with(asUser(manager)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/manage/categories").with(asUser(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString(name)));
        mvc.perform(get("/manage/categories").with(asUser(user))).andExpect(status().isForbidden());
        mvc.perform(post("/manage/categories").param("name", "x").with(csrf()).with(asUser(user))).andExpect(status().isForbidden());

        CategoryView c = categories.active().stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
        mvc.perform(get("/").with(asUser(user))).andExpect(content().string(containsString(name)));
        mvc.perform(get("/categories/" + c.slug()).with(asUser(user))).andExpect(status().isOk());
        mvc.perform(get("/categories/khong-ton-tai-" + UUID.randomUUID()).with(asUser(user))).andExpect(status().isNotFound());
    }
}
