package com.aloute.search;

import com.aloute.dto.search.HashtagResult;
import com.aloute.service.search.SearchService;

import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.dto.post.PostView;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.Profile;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.UserStatus;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Phần 2c: tìm người dùng, bài viết, hashtag; hashtag đang hot. */
class SearchServiceIT extends IntegrationTest {

    @Autowired SearchService search;
    @Autowired PostService posts;
    @Autowired JdbcTemplate jdbc;

    // ---------- Bài viết ----------

    @Test
    void findsPublicPostsIgnoringAccentsAndCase() {
        User author = createUser();
        Post match = posts.create(author.getId(), "Hôm nay trời đẹp ghê", Visibility.PUBLIC, List.of(), null);
        Post noMatch = posts.create(author.getId(), "chuyện khác hoàn toàn", Visibility.PUBLIC, List.of(), null);

        List<PostView> result = search.searchPosts("HOM NAY TROI DEP", null);

        assertThat(result).extracting(PostView::id).contains(match.getId()).doesNotContain(noMatch.getId());
    }

    @Test
    void doesNotFindPrivatePostsOrPostsOfSuspendedAuthors() {
        User owner = createUser();
        Post privatePost = posts.create(owner.getId(), "bí mật quốc gia", Visibility.PRIVATE, List.of(), null);
        User suspended = createUser();
        Post fromSuspended = posts.create(suspended.getId(), "bí mật quốc gia", Visibility.PUBLIC, List.of(), null);
        suspended.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(suspended);

        List<PostView> result = search.searchPosts("bí mật quốc gia", null);

        assertThat(result).extracting(PostView::id).doesNotContain(privatePost.getId(), fromSuspended.getId());
    }

    @Test
    void percentAndUnderscoreInTheQueryAreTreatedLiterally() {
        User author = createUser();
        Post match = posts.create(author.getId(), "giảm giá 50% hôm nay", Visibility.PUBLIC, List.of(), null);
        // Nếu "%" không được escape, mẫu ILIKE sẽ thành "...50%%..." tức "50" theo sau bởi BẤT KỲ gì,
        // nên câu này (có "50" nhưng không có ký tự "%" thật) cũng sẽ lọt qua — escape đúng thì không.
        Post decoy = posts.create(author.getId(), "giảm giá 50 xu hôm nay", Visibility.PUBLIC, List.of(), null);

        List<PostView> result = search.searchPosts("giam gia 50%", null);

        assertThat(result).extracting(PostView::id).contains(match.getId()).doesNotContain(decoy.getId());
    }

    @Test
    void queryOutsideTwoToHundredCharsFindsNothing() {
        User author = createUser();
        posts.create(author.getId(), "a", Visibility.PUBLIC, List.of(), null);

        assertThat(search.searchPosts("a", null)).isEmpty();
        assertThat(search.searchPosts("a".repeat(101), null)).isEmpty();
        assertThat(search.searchPosts("", null)).isEmpty();
    }

    // ---------- Người dùng ----------

    private User newUserNamed(String username) {
        User user = new User();
        user.setEmail(username + "@test.local");
        user.setUsername(username);
        user.setPasswordHash(java.util.UUID.randomUUID().toString());
        user.setRoles(EnumSet.of(Role.USER));
        Profile profile = new Profile();
        profile.setDisplayName("Test " + username);
        user.attachProfile(profile);
        return users.saveAndFlush(user);
    }

    @Test
    void findsByUsername() {
        // username không có khoảng trắng nên tìm theo một chuỗi con liền của chính username
        User byUsername = newUserNamed("nguyenvana" + java.util.UUID.randomUUID().toString().substring(0, 8));
        User unrelated = createUser();

        List<PostView.AuthorView> result = search.searchUsers("nguyenvana");

        assertThat(result).extracting(PostView.AuthorView::id).contains(byUsername.getId()).doesNotContain(unrelated.getId());
    }

    @Test
    void findsByDisplayNameIgnoringAccentsAndCase() {
        User byDisplayName = createUser();
        byDisplayName.getProfile().setDisplayName("Nguyễn Văn A");
        users.saveAndFlush(byDisplayName);
        User unrelated = createUser();

        List<PostView.AuthorView> result = search.searchUsers("NGUYEN VAN A");

        assertThat(result).extracting(PostView.AuthorView::id).contains(byDisplayName.getId()).doesNotContain(unrelated.getId());
    }

    @Test
    void suspendedUsersAreNotFound() {
        User suspended = createUser();
        suspended.getProfile().setDisplayName("Tìm Tôi Đi");
        suspended.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(suspended);

        assertThat(search.searchUsers("tim toi di")).extracting(PostView.AuthorView::id).doesNotContain(suspended.getId());
    }

    // ---------- Hashtag ----------

    @Test
    void findsHashtagsByPrefixOrderedByPostCount() {
        // Thẻ có tiền tố ngẫu nhiên riêng của test này, để không lẫn với thẻ trùng tên do test khác tạo
        // trên cùng cơ sở dữ liệu (post_hashtags dùng chung cho cả bộ test).
        String prefix = "qa" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User author = createUser();
        posts.create(author.getId(), "#" + prefix + "js one", Visibility.PUBLIC, List.of(), null);
        posts.create(author.getId(), "#" + prefix + "js two", Visibility.PUBLIC, List.of(), null);
        posts.create(author.getId(), "#" + prefix + "native", Visibility.PUBLIC, List.of(), null);

        List<HashtagResult> result = search.searchHashtags(prefix);

        assertThat(result).extracting(HashtagResult::tag).containsExactly(prefix + "js", prefix + "native");
        assertThat(result.get(0).postCount()).isEqualTo(2);
    }

    @Test
    void trendingOnlyCountsRecentPublicLivePosts() {
        // Đăng nhiều bài để chắc chắn đứng đầu bất kể các hashtag lặt vặt do test khác tạo cùng lúc
        String hot = "hot" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String old = "old" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User author = createUser();
        for (int i = 0; i < 5; i++) {
            posts.create(author.getId(), "#" + hot, Visibility.PUBLIC, List.of(), null);
        }
        Post oldPost = posts.create(author.getId(), "#" + old, Visibility.PUBLIC, List.of(), null);
        jdbc.update("update posts set created_at = now() - interval '30 days' where id = ?", oldPost.getId());
        Post deleted = posts.create(author.getId(), "#" + hot, Visibility.PUBLIC, List.of(), null);
        posts.delete(author.getId(), deleted.getId());

        List<String> tags = search.trending().stream().map(HashtagResult::tag).collect(Collectors.toList());

        assertThat(tags).contains(hot).doesNotContain(old);
        assertThat(search.trending().stream().filter(h -> h.tag().equals(hot)).findFirst().orElseThrow().postCount())
                .as("bài đã xóa không được tính")
                .isEqualTo(5);
    }
}
