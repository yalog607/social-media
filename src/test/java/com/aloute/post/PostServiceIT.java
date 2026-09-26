package com.aloute.post;

import com.aloute.common.RateAction;
import com.aloute.common.RateLimitExceededException;
import com.aloute.media.InvalidMediaException;
import com.aloute.media.MediaKind;
import com.aloute.support.IntegrationTest;
import com.aloute.support.TestMedia;
import com.aloute.user.User;
import com.aloute.user.UserStatus;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 2a, task 6: tạo/sửa/xóa/xem bài và các quy tắc quyền. */
class PostServiceIT extends IntegrationTest {

    @Autowired PostService service;
    @Autowired PostRepository posts;
    @Autowired TransactionTemplate tx;

    private Post reload(UUID id) {
        return posts.findById(id).orElseThrow();
    }

    private Post publicPost(User author) {
        return service.create(author.getId(), "bài của " + author.getUsername(), Visibility.PUBLIC, List.of(), null);
    }

    // ---------- Tạo bài ----------

    @Test
    void createsATextPostWithNormalizedSearchTextAndHashtags() {
        User author = createUser();

        Post created = service.create(author.getId(), "  Xin chào #Nhân #học_tập  ", Visibility.PUBLIC, List.of(), null);

        Post loaded = tx.execute(s -> {
            Post p = reload(created.getId());
            p.getHashtags().size(); // nạp collection lười
            return p;
        });
        assertThat(loaded.getContent()).isEqualTo("Xin chào #Nhân #học_tập");
        assertThat(loaded.getSearchText()).isEqualTo("xin chao #nhan #hoc_tap");
        assertThat(loaded.getHashtags()).containsExactlyInAnyOrder("nhan", "hoc_tap");
        assertThat(loaded.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(loaded.getEditedAt()).isNull();
    }

    @Test
    void createsAPostWithImagesInOrder() {
        User author = createUser();

        Post created = service.create(author.getId(), "", Visibility.PUBLIC,
                List.of(TestMedia.image("a.jpg", TestMedia.jpeg(300, 200)), TestMedia.image("b.jpg", TestMedia.jpeg(100, 100))), null);

        tx.executeWithoutResult(s -> {
            List<PostMedia> media = reload(created.getId()).getMedia();
            assertThat(media).hasSize(2);
            assertThat(media).extracting(PostMedia::getKind).containsOnly(MediaKind.IMAGE);
            assertThat(media).extracting(PostMedia::getSortOrder).containsExactly((short) 0, (short) 1);
            assertThat(media.get(0).getUrl()).startsWith("/uploads/posts/");
        });
    }

    @Test
    void createsAPostWithAVideoAndNoText() {
        User author = createUser();

        Post created = service.create(author.getId(), null, Visibility.PUBLIC, List.of(),
                TestMedia.video("clip.mp4", TestMedia.mp4(5_000)));

        tx.executeWithoutResult(s -> {
            List<PostMedia> media = reload(created.getId()).getMedia();
            assertThat(media).hasSize(1);
            assertThat(media.get(0).getKind()).isEqualTo(MediaKind.VIDEO);
            assertThat(media.get(0).getContentType()).isEqualTo("video/mp4");
        });
    }

    @Test
    void rejectsAPostWithNeitherTextNorMediaWithoutStoringAnything() {
        long before = TestMedia.storedFileCount();

        assertThatThrownBy(() -> service.create(createUser().getId(), "   \n\t ", Visibility.PUBLIC, List.of(), null))
                .isInstanceOf(InvalidPostException.class).hasMessageContaining("viết gì đó");
        assertThat(TestMedia.storedFileCount()).isEqualTo(before);
    }

    @Test
    void countsCharactersNotUtf16UnitsWhenCheckingTheLimit() {
        User author = createUser();
        String twoThousandEmoji = "😀".repeat(Post.MAX_CONTENT_LENGTH); // 4000 đơn vị UTF-16 nhưng đúng 2000 ký tự

        assertThatCode(() -> service.create(author.getId(), twoThousandEmoji, Visibility.PUBLIC, List.of(), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.create(author.getId(), twoThousandEmoji + "x", Visibility.PUBLIC, List.of(), null))
                .isInstanceOf(InvalidPostException.class).hasMessageContaining("2000");
    }

    @Test
    void stripsNulAndOtherControlCharactersButKeepsLineBreaks() {
        User author = createUser();

        Post created = service.create(author.getId(), "dòng 1\u0000\u0007\ndòng 2", Visibility.PUBLIC, List.of(), null);

        assertThat(reload(created.getId()).getContent()).isEqualTo("dòng 1\ndòng 2");
    }

    @Test
    void usesTheProfileDefaultVisibilityWhenNoneIsGiven() {
        User author = createUser();
        author.getProfile().setDefaultPostVisibility(Visibility.PRIVATE);
        users.saveAndFlush(author);

        Post created = service.create(author.getId(), "mặc định", null, List.of(), null);

        assertThat(reload(created.getId()).getVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void invalidMediaFailsTheWholePostAndLeavesNoFilesBehind() {
        User author = createUser();
        long before = TestMedia.storedFileCount();

        assertThatThrownBy(() -> service.create(author.getId(), "có ảnh xấu", Visibility.PUBLIC,
                List.of(TestMedia.image("ok.jpg", TestMedia.jpeg(60, 60)),
                        TestMedia.image("bad.jpg", "không phải ảnh".getBytes())), null))
                .isInstanceOf(InvalidMediaException.class);

        assertThat(TestMedia.storedFileCount()).isEqualTo(before);
        assertThat(posts.findAll().stream().filter(p -> p.getContent().equals("có ảnh xấu"))).isEmpty();
    }

    @Test
    void aSuspendedAccountCannotPost() {
        User author = createUser();
        author.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(author);

        assertThatThrownBy(() -> publicPost(author)).isInstanceOf(InvalidPostException.class);
    }

    @Test
    void limitsPostsToTenEveryTenMinutes() {
        User author = createUser();
        for (int i = 0; i < RateAction.POST.max(); i++) {
            service.create(author.getId(), "bài " + i, Visibility.PUBLIC, List.of(), null);
        }

        assertThatThrownBy(() -> service.create(author.getId(), "bài thứ 11", Visibility.PUBLIC, List.of(), null))
                .isInstanceOf(RateLimitExceededException.class);
        clock.advance(RateAction.POST.window().plusSeconds(1));
        assertThatCode(() -> service.create(author.getId(), "lại được rồi", Visibility.PUBLIC, List.of(), null))
                .doesNotThrowAnyException();
    }

    // ---------- Quyền xem ----------

    @Test
    void publicPostsAreVisibleToEveryoneIncludingGuests() {
        User author = createUser();
        Post post = publicPost(author);

        assertThat(service.getVisible(post.getId(), null).getId()).isEqualTo(post.getId());
        assertThat(service.getVisible(post.getId(), createUser().getId()).getId()).isEqualTo(post.getId());
        assertThat(service.getVisible(post.getId(), author.getId()).getId()).isEqualTo(post.getId());
    }

    @Test
    void privateAndFriendsPostsAreVisibleOnlyToTheirAuthor() {
        User author = createUser();
        User other = createUser();
        for (Visibility visibility : List.of(Visibility.PRIVATE, Visibility.FRIENDS)) {
            Post post = service.create(author.getId(), "chỉ mình tôi " + visibility, visibility, List.of(), null);

            assertThat(service.getVisible(post.getId(), author.getId()).getId()).isEqualTo(post.getId());
            assertThatThrownBy(() -> service.getVisible(post.getId(), other.getId()))
                    .as(visibility.name()).isInstanceOf(PostNotFoundException.class);
            assertThatThrownBy(() -> service.getVisible(post.getId(), null))
                    .as(visibility.name()).isInstanceOf(PostNotFoundException.class);
        }
    }

    @Test
    void unknownIdsAndPostsOfSuspendedAuthorsAreNotFound() {
        User author = createUser();
        Post post = publicPost(author);

        assertThatThrownBy(() -> service.getVisible(UUID.randomUUID(), null)).isInstanceOf(PostNotFoundException.class);

        author.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(author);
        assertThatThrownBy(() -> service.getVisible(post.getId(), null)).isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> service.getVisible(post.getId(), author.getId())).isInstanceOf(PostNotFoundException.class);
    }

    // ---------- Sửa ----------

    @Test
    void authorCanEditTextAndVisibilityAndHashtagsFollow() {
        User author = createUser();
        Post post = service.create(author.getId(), "cũ #a #b", Visibility.PUBLIC, List.of(), null);

        service.edit(author.getId(), post.getId(), "mới #b #c", Visibility.PRIVATE);

        Post loaded = tx.execute(s -> {
            Post p = reload(post.getId());
            p.getHashtags().size();
            return p;
        });
        assertThat(loaded.getContent()).isEqualTo("mới #b #c");
        assertThat(loaded.getSearchText()).isEqualTo("moi #b #c");
        assertThat(loaded.getHashtags()).containsExactlyInAnyOrder("b", "c");
        assertThat(loaded.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(loaded.getEditedAt()).isNotNull();
    }

    @Test
    void changingOnlyVisibilityDoesNotMarkThePostAsEdited() {
        User author = createUser();
        Post post = service.create(author.getId(), "giữ nguyên chữ", Visibility.PUBLIC, List.of(), null);

        service.edit(author.getId(), post.getId(), "giữ nguyên chữ", Visibility.PRIVATE);

        Post loaded = reload(post.getId());
        assertThat(loaded.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(loaded.getEditedAt()).isNull();
    }

    @Test
    void othersCannotEditAndLearnNothingAboutThePost() {
        User author = createUser();
        Post post = service.create(author.getId(), "của tôi", Visibility.PRIVATE, List.of(), null);

        assertThatThrownBy(() -> service.edit(createUser().getId(), post.getId(), "chiếm quyền", Visibility.PUBLIC))
                .isInstanceOf(PostNotFoundException.class);

        Post loaded = reload(post.getId());
        assertThat(loaded.getContent()).isEqualTo("của tôi");
        assertThat(loaded.getVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void editingCannotEmptyATextOnlyPostButAMediaPostMayLoseItsText() {
        User author = createUser();
        Post textOnly = service.create(author.getId(), "chỉ có chữ", Visibility.PUBLIC, List.of(), null);
        Post withImage = service.create(author.getId(), "có ảnh", Visibility.PUBLIC,
                List.of(TestMedia.image("a.jpg", TestMedia.jpeg(50, 50))), null);

        assertThatThrownBy(() -> service.edit(author.getId(), textOnly.getId(), "  ", null))
                .isInstanceOf(InvalidPostException.class);
        assertThatCode(() -> service.edit(author.getId(), withImage.getId(), "", null)).doesNotThrowAnyException();
    }

    @Test
    void editingRejectsTooLongText() {
        User author = createUser();
        Post post = publicPost(author);

        assertThatThrownBy(() -> service.edit(author.getId(), post.getId(), "x".repeat(2001), null))
                .isInstanceOf(InvalidPostException.class);
    }

    // ---------- Xóa ----------

    @Test
    void authorSoftDeletesAndNobodyCanSeeItAfterwards() {
        User author = createUser();
        Post post = publicPost(author);

        service.delete(author.getId(), post.getId());

        assertThat(reload(post.getId()).getDeletedAt()).as("dữ liệu vẫn còn (xóa mềm)").isNotNull();
        assertThatThrownBy(() -> service.getVisible(post.getId(), author.getId())).isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> service.getVisible(post.getId(), null)).isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> service.delete(author.getId(), post.getId()))
                .as("xóa lần hai").isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> service.edit(author.getId(), post.getId(), "sửa bài đã xóa", null))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void othersCannotDelete() {
        User author = createUser();
        Post post = publicPost(author);

        assertThatThrownBy(() -> service.delete(createUser().getId(), post.getId())).isInstanceOf(PostNotFoundException.class);

        assertThat(reload(post.getId()).getDeletedAt()).isNull();
    }

    // ---------- Dọn file khi giao dịch không commit ----------

    @Test
    void removesStoredFilesWhenTheSurroundingTransactionRollsBack() {
        User author = createUser();
        long before = TestMedia.storedFileCount();

        tx.executeWithoutResult(status -> {
            service.create(author.getId(), "sẽ bị hoàn tác", Visibility.PUBLIC,
                    List.of(TestMedia.image("a.jpg", TestMedia.jpeg(80, 80))), null);
            assertThat(TestMedia.storedFileCount()).as("file đã được ghi trong lúc giao dịch còn mở").isEqualTo(before + 1);
            status.setRollbackOnly();
        });

        assertThat(TestMedia.storedFileCount()).isEqualTo(before);
        assertThat(posts.findAll().stream().filter(p -> p.getContent().equals("sẽ bị hoàn tác"))).isEmpty();
    }
}
