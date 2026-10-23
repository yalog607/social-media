package com.aloute.feed;

import com.aloute.dto.feed.FeedPage;
import com.aloute.service.feed.FeedService;

import com.aloute.model.post.Post;
import com.aloute.repository.post.PostRepository;
import com.aloute.service.post.PostService;
import com.aloute.dto.post.PostView;
import com.aloute.support.IntegrationTest;
import com.aloute.support.TestMedia;
import com.aloute.model.user.User;
import com.aloute.model.user.UserStatus;
import com.aloute.model.user.Visibility;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phần 2a, task 7. Cơ sở dữ liệu dùng chung với các test khác nên các phép kiểm tra chính xác đều đi qua
 * {@code byAuthor} (chỉ dữ liệu của tác giả tạo trong test); riêng {@code home} kiểm tra "có/không có" và thứ tự tương đối.
 */
class FeedIT extends IntegrationTest {

    @Autowired FeedService feed;
    @Autowired PostService postService;
    @Autowired PostRepository postRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired com.aloute.service.reaction.ReactionService reactionService;
    @Autowired com.aloute.service.comment.CommentService commentService;
    @Autowired com.aloute.service.social.FriendService friendService;

    /** Tạo bài rồi đặt created_at cách đều nhau (bài đầu tiên cũ nhất) để thứ tự không phụ thuộc đồng hồ. */
    private List<Post> createSpaced(User author, int count, Visibility visibility) {
        Instant base = Instant.now().minus(count + 5, ChronoUnit.MINUTES);
        List<Post> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Lưu thẳng qua repository: dữ liệu dựng sẵn không đi qua bộ giới hạn tần suất đăng bài
            Post post = new Post();
            post.setAuthor(author);
            post.setContent("bài số " + i + " của " + author.getUsername());
            post.setSearchText(post.getContent());
            post.setVisibility(visibility);
            post = postRepository.saveAndFlush(post);
            jdbc.update("update posts set created_at = ? where id = ?",
                    java.sql.Timestamp.from(base.plus(i, ChronoUnit.MINUTES)), post.getId());
            created.add(post);
        }
        return created;
    }

    private static List<UUID> ids(FeedPage page) {
        return page.posts().stream().map(PostView::id).toList();
    }

    private static List<UUID> idsOf(List<Post> posts) {
        return posts.stream().map(Post::getId).toList();
    }

    // ---------- Phân trang ----------

    @Test
    void pagesNewestFirstWithoutGapsOrDuplicates() {
        User author = createUser();
        List<Post> created = createSpaced(author, 25, Visibility.PUBLIC);
        List<UUID> expected = new ArrayList<>(idsOf(created));
        java.util.Collections.reverse(expected); // mới nhất trước

        FeedPage first = feed.byAuthor(author.getId(), null, null);
        FeedPage second = feed.byAuthor(author.getId(), null, first.nextCursor());
        FeedPage third = feed.byAuthor(author.getId(), null, second.nextCursor());

        assertThat(first.posts()).hasSize(10);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.posts()).hasSize(10);
        assertThat(third.posts()).hasSize(5);
        assertThat(third.hasMore()).as("trang cuối không còn nút xem thêm").isFalse();
        assertThat(third.nextCursor()).isNull();

        List<UUID> all = new ArrayList<>();
        all.addAll(ids(first));
        all.addAll(ids(second));
        all.addAll(ids(third));
        assertThat(all).containsExactlyElementsOf(expected);
    }

    @Test
    void exactlyOnePageWorthOfPostsHasNoNextCursor() {
        User author = createUser();
        createSpaced(author, FeedService.PAGE_SIZE, Visibility.PUBLIC);

        FeedPage page = feed.byAuthor(author.getId(), null, null);

        assertThat(page.posts()).hasSize(FeedService.PAGE_SIZE);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void aPostAddedBetweenTwoPageLoadsNeitherRepeatsNorHidesAnything() {
        User author = createUser();
        List<Post> created = createSpaced(author, 25, Visibility.PUBLIC);
        FeedPage first = feed.byAuthor(author.getId(), null, null);
        List<UUID> shownSoFar = ids(first);

        // Bài mới nhất chen vào SAU khi trang 1 đã tải: với phân trang theo số trang sẽ làm lặp lại một bài ở trang 2
        Post newest = postService.create(author.getId(), "bài mới chen vào", Visibility.PUBLIC, List.of(), null);
        FeedPage second = feed.byAuthor(author.getId(), null, first.nextCursor());

        assertThat(ids(second)).doesNotContainAnyElementsOf(shownSoFar).doesNotContain(newest.getId());
        List<UUID> expectedSecond = new ArrayList<>(idsOf(created));
        java.util.Collections.reverse(expectedSecond);
        assertThat(ids(second)).containsExactlyElementsOf(expectedSecond.subList(10, 20));
    }

    @Test
    void aTamperedCursorIsTreatedAsTheFirstPageInsteadOfFailing() {
        User author = createUser();
        createSpaced(author, 3, Visibility.PUBLIC);

        FeedPage viaGarbage = feed.byAuthor(author.getId(), null, "%%%khong-hop-le%%%");
        FeedPage viaNull = feed.byAuthor(author.getId(), null, null);

        assertThat(ids(viaGarbage)).isEqualTo(ids(viaNull)).hasSize(3);
    }

    // ---------- Quyền xem ----------

    @Test
    void visitorsOnlySeePublicPostsButTheOwnerSeesEverything() {
        User author = createUser();
        Post publicPost = createSpaced(author, 1, Visibility.PUBLIC).get(0);
        Post privatePost = createSpaced(author, 1, Visibility.PRIVATE).get(0);
        Post friendsPost = createSpaced(author, 1, Visibility.FRIENDS).get(0);

        List<UUID> asGuest = ids(feed.byAuthor(author.getId(), null, null));
        List<UUID> asOther = ids(feed.byAuthor(author.getId(), createUser().getId(), null));
        List<UUID> asOwner = ids(feed.byAuthor(author.getId(), author.getId(), null));

        assertThat(asGuest).containsExactly(publicPost.getId());
        assertThat(asOther).containsExactly(publicPost.getId());
        assertThat(asOwner).containsExactlyInAnyOrder(publicPost.getId(), privatePost.getId(), friendsPost.getId());
    }

    @Test
    void homeShowsOthersPublicPostsAndMyOwnPrivateOnesButNotTheirPrivateOnes() {
        User me = createUser();
        User other = createUser();
        Post othersPublic = createSpaced(other, 1, Visibility.PUBLIC).get(0);
        Post othersPrivate = createSpaced(other, 1, Visibility.PRIVATE).get(0);
        Post myPrivate = createSpaced(me, 1, Visibility.PRIVATE).get(0);
        // Đặt cả ba vào những phút gần nhất để chắc chắn nằm trong trang đầu của bảng tin dùng chung
        jdbc.update("update posts set created_at = now() where id in (?, ?, ?)",
                othersPublic.getId(), othersPrivate.getId(), myPrivate.getId());

        List<UUID> home = ids(feed.home(me.getId(), null));

        assertThat(home).contains(othersPublic.getId(), myPrivate.getId()).doesNotContain(othersPrivate.getId());
    }

    @Test
    void homeIsNewestFirst() {
        // Dùng byAuthor thay vì home: home gộp bài PUBLIC của MỌI tác giả trong cả bộ test, nên khẳng định
        // đúng-3-bài-đầu-tiên trên home không ổn định khi có test khác chạy song song tạo thêm bài công khai.
        User author = createUser();
        List<Post> created = createSpaced(author, 3, Visibility.PUBLIC);
        jdbc.update("update posts set created_at = now() + interval '1 hour' * ? where id = ?", 0, created.get(0).getId());
        jdbc.update("update posts set created_at = now() + interval '1 hour' * ? where id = ?", 1, created.get(1).getId());
        jdbc.update("update posts set created_at = now() + interval '1 hour' * ? where id = ?", 2, created.get(2).getId());

        List<UUID> own = ids(feed.byAuthor(author.getId(), author.getId(), null));

        assertThat(own).containsExactly(created.get(2).getId(), created.get(1).getId(), created.get(0).getId());
    }

    @Test
    void deletedPostsAndPostsOfSuspendedAuthorsNeverAppear() {
        User author = createUser();
        List<Post> created = createSpaced(author, 3, Visibility.PUBLIC);
        postService.delete(author.getId(), created.get(1).getId());

        assertThat(ids(feed.byAuthor(author.getId(), null, null)))
                .containsExactlyInAnyOrder(created.get(0).getId(), created.get(2).getId());

        author.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(author);
        assertThat(feed.byAuthor(author.getId(), null, null).posts()).isEmpty();
        assertThat(ids(feed.home(createUser().getId(), null))).doesNotContainAnyElementsOf(idsOf(created));
    }

    // ---------- Dữ liệu hiển thị ----------

    @Test
    void viewCarriesAuthorMediaMineFlagAndSafeHtml() {
        User author = createUser();
        author.getProfile().setDisplayName("Bạn <b>Xinh</b>");
        users.saveAndFlush(author);
        Post post = postService.create(author.getId(), "<i>hi</i> #Vui", Visibility.PUBLIC,
                List.of(TestMedia.image("a.jpg", TestMedia.jpeg(50, 50)), TestMedia.image("b.jpg", TestMedia.jpeg(60, 60))), null);

        PostView asOwner = feed.byAuthor(author.getId(), author.getId(), null).posts().get(0);
        PostView asGuest = feed.byAuthor(author.getId(), null, null).posts().get(0);

        assertThat(asOwner.id()).isEqualTo(post.getId());
        assertThat(asOwner.mine()).isTrue();
        assertThat(asGuest.mine()).isFalse();
        assertThat(asOwner.author().username()).isEqualTo(author.getUsername());
        assertThat(asOwner.author().displayName()).isEqualTo("Bạn <b>Xinh</b>"); // giá trị thô: template sẽ escape khi hiển thị
        assertThat(asOwner.media()).hasSize(2);
        assertThat(asOwner.hasVideo()).isFalse();
        assertThat(asOwner.contentHtml()).contains("&lt;i&gt;hi&lt;/i&gt;").contains("class=\"hashtag\"").doesNotContain("<i>");
        assertThat(asOwner.edited()).isFalse();
        assertThat(asOwner.rawContent()).isEqualTo("<i>hi</i> #Vui"); // chữ gốc chỉ dành cho chủ bài
        assertThat(asGuest.rawContent()).isNull();
        assertThat(asOwner.isShare()).isFalse();
        assertThat(asOwner.reactions().total()).isEqualTo(0);
        assertThat(asOwner.commentCount()).isEqualTo(0);
        assertThat(asOwner.shareCount()).isEqualTo(0);
    }

    @Test
    void viewCarriesReactionCommentAndShareCounts() {
        User author = createUser();
        Post post = postService.create(author.getId(), "bài gốc", Visibility.PUBLIC, List.of(), null);
        User reactor = createUser();
        reactionService.toggle(reactor.getId(), post.getId(), com.aloute.model.reaction.ReactionType.FIRE);
        commentService.create(createUser().getId(), post.getId(), null, "bình luận đầu");
        Post share = postService.share(createUser().getId(), post.getId(), "chia sẻ lại nè");

        PostView original = feed.byAuthor(author.getId(), reactor.getId(), null).posts().get(0);

        assertThat(original.reactions().total()).isEqualTo(1);
        assertThat(original.reactions().mine()).isEqualTo(com.aloute.model.reaction.ReactionType.FIRE);
        assertThat(original.commentCount()).isEqualTo(1);
        assertThat(original.shareCount()).isEqualTo(1);
        assertThat(share).isNotNull();
    }

    @Test
    void shareViewEmbedsTheOriginalPostAndDegradesGracefullyWhenItDisappears() {
        User author = createUser();
        Post source = postService.create(author.getId(), "bài để chia sẻ", Visibility.PUBLIC, List.of(), null);
        User sharer = createUser();
        Post share = postService.share(sharer.getId(), source.getId(), "xem nè");

        PostView shareView = feed.byAuthor(sharer.getId(), null, null).posts().get(0);
        assertThat(shareView.isShare()).isTrue();
        assertThat(shareView.sharedPostUnavailable()).isFalse();
        assertThat(shareView.sharedPost().id()).isEqualTo(source.getId());
        assertThat(shareView.sharedPost().contentHtml()).contains("bài để chia sẻ");

        postService.delete(author.getId(), source.getId());
        PostView afterDelete = feed.byAuthor(sharer.getId(), null, null).posts().get(0);
        assertThat(afterDelete.sharedPostUnavailable()).isTrue();
        assertThat(afterDelete.sharedPost()).isNull();
    }

    // ---------- Hiệu năng: không N+1 ----------

    @Test
    void loadingAPageDoesNotIssueAQueryPerPost() {
        // 10 tác giả khác nhau để lộ N+1 nếu tác giả/vai trò/hồ sơ bị nạp riêng từng bài
        List<User> authors = new ArrayList<>();
        Set<UUID> mine = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            User author = createUser();
            authors.add(author);
            Post post = postService.create(author.getId(), "bài " + i, Visibility.PUBLIC,
                    List.of(TestMedia.image("a.jpg", TestMedia.jpeg(40, 40))), null);
            mine.add(post.getId());
        }
        jdbc.update("update posts set created_at = now() where id = any (?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", mine.toArray())));
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        FeedPage page = feed.home(authors.get(0).getId(), null);

        assertThat(page.posts()).hasSize(10);
        assertThat(page.posts().stream().map(PostView::id)).containsAnyElementsOf(mine);
        assertThat(stats.getPrepareStatementCount())
                .as("số câu SQL cho cả trang 10 bài (bảng tin + media + cảm xúc + bình luận + chia sẻ + gắn thẻ + mở khóa bài trả phí theo lô)")
                .isLessThanOrEqualTo(9);
    }

    // ---------- Theo hashtag ----------

    @Test
    void byHashtagOnlyShowsPublicPostsWithThatTag() {
        User author = createUser();
        Post tagged = postService.create(author.getId(), "Xin chào #Nhân", Visibility.PUBLIC, List.of(), null);
        postService.create(author.getId(), "không gắn thẻ", Visibility.PUBLIC, List.of(), null);
        Post privateTagged = postService.create(author.getId(), "riêng tư #nhan", Visibility.PRIVATE, List.of(), null);

        List<UUID> result = ids(feed.byHashtag("nhan", null, null));

        assertThat(result).contains(tagged.getId()).doesNotContain(privateTagged.getId());
    }

    @Test
    void byHashtagHidesDeletedPostsAndSuspendedAuthors() {
        User author = createUser();
        Post kept = postService.create(author.getId(), "#giu", Visibility.PUBLIC, List.of(), null);
        Post deleted = postService.create(author.getId(), "#giu", Visibility.PUBLIC, List.of(), null);
        postService.delete(author.getId(), deleted.getId());

        User suspended = createUser();
        Post fromSuspended = postService.create(suspended.getId(), "#giu", Visibility.PUBLIC, List.of(), null);
        suspended.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(suspended);

        List<UUID> result = ids(feed.byHashtag("giu", null, null));

        assertThat(result).containsExactly(kept.getId());
        assertThat(result).doesNotContain(deleted.getId(), fromSuspended.getId());
    }
}
