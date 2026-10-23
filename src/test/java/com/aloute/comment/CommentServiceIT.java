package com.aloute.comment;

import com.aloute.dto.comment.CommentView;
import com.aloute.exception.comment.InvalidCommentException;
import com.aloute.model.comment.Comment;
import com.aloute.service.comment.CommentService;

import com.aloute.model.post.Post;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 2b: bình luận, trả lời một cấp, xóa mềm. */
class CommentServiceIT extends IntegrationTest {

    @Autowired CommentService comments;
    @Autowired PostService posts;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài của " + author.getUsername(), Visibility.PUBLIC, List.of(), null);
    }

    @Test
    void createsARootComment() {
        User author = createUser();
        Post post = publicPost(author);
        User commenter = createUser();

        Comment comment = comments.create(commenter.getId(), post.getId(), null, "  Hay ghê!  ");

        assertThat(comment.getContent()).isEqualTo("Hay ghê!");
        assertThat(comment.isReply()).isFalse();
    }

    @Test
    void replyingToAReplyIsFlattenedToTheRootComment() {
        User author = createUser();
        Post post = publicPost(author);
        Comment root = comments.create(createUser().getId(), post.getId(), null, "gốc");
        Comment reply = comments.create(createUser().getId(), post.getId(), root.getId(), "trả lời gốc");

        Comment replyToReply = comments.create(createUser().getId(), post.getId(), reply.getId(), "trả lời cái trả lời");

        assertThat(replyToReply.getParent().getId())
                .as("trả lời của trả lời phải gắn thẳng về bình luận gốc, không lồng thêm cấp")
                .isEqualTo(root.getId());
    }

    @Test
    void listGroupsRepliesUnderTheirRootInChronologicalOrder() {
        User author = createUser();
        Post post = publicPost(author);
        Comment root = comments.create(createUser().getId(), post.getId(), null, "gốc");
        comments.create(createUser().getId(), post.getId(), root.getId(), "trả lời 1");
        comments.create(createUser().getId(), post.getId(), root.getId(), "trả lời 2");

        List<CommentView> views = comments.list(post.getId(), null);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).replies()).extracting(CommentView::contentHtml).containsExactly("trả lời 1", "trả lời 2");
    }

    @Test
    void deletedLeafReplyDisappearsEntirely() {
        User author = createUser();
        Post post = publicPost(author);
        Comment root = comments.create(createUser().getId(), post.getId(), null, "gốc");
        Comment reply = comments.create(createUser().getId(), post.getId(), root.getId(), "trả lời");

        comments.delete(reply.getAuthor().getId(), reply.getId());

        assertThat(comments.list(post.getId(), null).get(0).replies()).isEmpty();
    }

    @Test
    void deletedRootWithSurvivingRepliesKeepsAPlaceholder() {
        User author = createUser();
        Post post = publicPost(author);
        Comment root = comments.create(createUser().getId(), post.getId(), null, "gốc");
        Comment reply = comments.create(createUser().getId(), post.getId(), root.getId(), "vẫn còn");

        comments.delete(root.getAuthor().getId(), root.getId());

        List<CommentView> views = comments.list(post.getId(), null);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).deleted()).isTrue();
        assertThat(views.get(0).replies()).extracting(CommentView::contentHtml).containsExactly("vẫn còn");
    }

    @Test
    void deletedRootWithoutRepliesDisappearsEntirely() {
        User author = createUser();
        Post post = publicPost(author);
        Comment root = comments.create(createUser().getId(), post.getId(), null, "gốc");

        comments.delete(root.getAuthor().getId(), root.getId());

        assertThat(comments.list(post.getId(), null)).isEmpty();
    }

    @Test
    void postAuthorCanDeleteSomeoneElsesComment() {
        User author = createUser();
        Post post = publicPost(author);
        Comment comment = comments.create(createUser().getId(), post.getId(), null, "gốc");

        comments.delete(author.getId(), comment.getId());

        assertThat(comments.list(post.getId(), null)).isEmpty();
    }

    @Test
    void aStrangerCannotDeleteSomeoneElsesComment() {
        User author = createUser();
        Post post = publicPost(author);
        Comment comment = comments.create(createUser().getId(), post.getId(), null, "gốc");

        assertThatThrownBy(() -> comments.delete(createUser().getId(), comment.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void blankOrTooLongContentIsRejected() {
        User author = createUser();
        Post post = publicPost(author);
        UUID commenterId = createUser().getId();

        assertThatThrownBy(() -> comments.create(commenterId, post.getId(), null, "   "))
                .isInstanceOf(InvalidCommentException.class);
        assertThatThrownBy(() -> comments.create(commenterId, post.getId(), null, "a".repeat(Comment.MAX_CONTENT_LENGTH + 1)))
                .isInstanceOf(InvalidCommentException.class);
    }

    @Test
    void cannotCommentOnAPrivatePostOfSomeoneElse() {
        User owner = createUser();
        Post privatePost = posts.create(owner.getId(), "riêng tư", Visibility.PRIVATE, List.of(), null);

        assertThatThrownBy(() -> comments.create(createUser().getId(), privatePost.getId(), null, "xin chào"))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void replyingWithAParentFromAnotherPostIsRejected() {
        User author = createUser();
        Post postA = publicPost(author);
        Post postB = publicPost(author);
        Comment rootOnA = comments.create(createUser().getId(), postA.getId(), null, "gốc trên bài A");

        assertThatThrownBy(() -> comments.create(createUser().getId(), postB.getId(), rootOnA.getId(), "lạc đề"))
                .isInstanceOf(InvalidCommentException.class);
    }
}
