package com.aloute.comment;

import com.aloute.media.InvalidMediaException;
import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.support.TestMedia;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bình luận kèm một ảnh (dán Ctrl+V hoặc chọn file): lưu, hiển thị, kiểm tra, dọn rác khi lỗi. */
class CommentImageIT extends IntegrationTest {

    @Autowired CommentService comments;
    @Autowired PostService posts;
    @Autowired TransactionTemplate tx;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài", Visibility.PUBLIC, List.of(), null);
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("image", "anh.jpg", "image/jpeg", TestMedia.jpeg(60, 40));
    }

    @Test
    void imageOnlyAndTextPlusImageCommentsAreStoredAndListed() {
        User author = createUser();
        User fan = createUser();
        Post post = publicPost(author);

        comments.create(fan.getId(), post.getId(), null, "", jpeg());
        comments.create(fan.getId(), post.getId(), null, "có cả chữ", jpeg());

        List<CommentView> views = comments.list(post.getId(), author.getId());
        assertThat(views).hasSize(2);
        assertThat(views).allSatisfy(v -> assertThat(v.imageUrl()).startsWith("/uploads/comments/"));
        assertThat(views).extracting(CommentView::contentHtml).containsExactlyInAnyOrder("", "có cả chữ");
    }

    @Test
    void replyCanCarryAnImageToo() {
        User author = createUser();
        Post post = publicPost(author);
        Comment root = comments.create(author.getId(), post.getId(), null, "gốc");

        comments.create(createUser().getId(), post.getId(), root.getId(), null, jpeg());

        assertThat(comments.list(post.getId(), author.getId()).get(0).replies()).singleElement()
                .satisfies(r -> assertThat(r.imageUrl()).isNotNull());
    }

    @Test
    void emptyCommentWithoutImageIsStillRejectedAndBadImagesAreRefused() {
        User fan = createUser();
        Post post = publicPost(createUser());
        MockMultipartFile notAnImage = new MockMultipartFile("image", "x.jpg", "image/jpeg", "không phải ảnh".getBytes());
        MockMultipartFile tooBig = new MockMultipartFile("image", "big.jpg", "image/jpeg", new byte[9 * 1024 * 1024]);

        assertThatThrownBy(() -> comments.create(fan.getId(), post.getId(), null, "  ", null)).isInstanceOf(InvalidCommentException.class);
        assertThatThrownBy(() -> comments.create(fan.getId(), post.getId(), null, "x", notAnImage)).isInstanceOf(InvalidMediaException.class);
        assertThatThrownBy(() -> comments.create(fan.getId(), post.getId(), null, "x", tooBig)).isInstanceOf(InvalidMediaException.class);
        assertThat(comments.list(post.getId(), fan.getId())).isEmpty();
    }

    @Test
    void deletedCommentDoesNotExposeItsImage() {
        User author = createUser();
        User fan = createUser();
        Post post = publicPost(author);
        Comment root = comments.create(fan.getId(), post.getId(), null, "gốc", jpeg());
        comments.create(author.getId(), post.getId(), root.getId(), "trả lời");

        comments.delete(fan.getId(), root.getId());

        CommentView placeholder = comments.list(post.getId(), author.getId()).get(0);
        assertThat(placeholder.deleted()).isTrue();
        assertThat(placeholder.imageUrl()).isNull();
    }

    @Test
    void imageIsDiscardedFromDiskWhenTheTransactionRollsBack() {
        User fan = createUser();
        Post post = publicPost(createUser());
        java.nio.file.Path dir = java.nio.file.Path.of("target/test-uploads/comments");
        long before = countFiles(dir);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            comments.create(fan.getId(), post.getId(), null, "sẽ rollback", jpeg());
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(countFiles(dir)).as("file ảnh đã được dọn").isEqualTo(before);
    }

    private static long countFiles(java.nio.file.Path dir) {
        try (var stream = java.nio.file.Files.exists(dir) ? java.nio.file.Files.list(dir) : java.util.stream.Stream.<java.nio.file.Path>empty()) {
            return stream.count();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void multipartEndpointAcceptsImagesRejectsBadOnesAndNeedsLogin() throws Exception {
        User author = createUser();
        User fan = createUser();
        Post post = publicPost(author);
        String url = "/api/posts/" + post.getId() + "/comments";

        mvc.perform(multipart(url).file(jpeg()).with(csrf()).with(asUser(fan))).andExpect(status().isOk());
        mvc.perform(multipart(url).file(new MockMultipartFile("image", "x.jpg", "image/jpeg", "rác".getBytes()))
                        .param("content", "x").with(csrf()).with(asUser(fan)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc.perform(multipart(url).param("content", " ").with(csrf()).with(asUser(fan)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(url).file(jpeg()).with(csrf())).andExpect(status().isUnauthorized());

        mvc.perform(get(url).with(asUser(author))).andExpect(status().isOk())
                .andExpect(content().string(containsString("comment-image")));
    }
}
