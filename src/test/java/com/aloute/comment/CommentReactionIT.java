package com.aloute.comment;

import com.aloute.dto.notification.NotificationView;
import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.dto.reaction.Reactor;
import com.aloute.model.comment.Comment;
import com.aloute.model.notification.NotificationType;
import com.aloute.model.post.Post;
import com.aloute.model.reaction.ReactionType;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.service.comment.CommentReactionService;
import com.aloute.service.comment.CommentService;
import com.aloute.service.notification.NotificationService;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommentReactionIT extends IntegrationTest {

    @Autowired CommentReactionService commentReactions;
    @Autowired CommentService comments;
    @Autowired PostService posts;
    @Autowired NotificationService notifications;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài của " + author.getUsername(), Visibility.PUBLIC, List.of(), null);
    }

    @Test
    void reactingTwiceWithTheSameTypeUnreacts() {
        User author = createUser();
        Post post = publicPost(author);
        User commenter = createUser();
        Comment comment = comments.create(commenter.getId(), post.getId(), null, "bình luận test");
        User viewer = createUser();

        ReactionSummary first = commentReactions.toggle(viewer.getId(), comment.getId(), ReactionType.LOVE);
        assertThat(first.mine()).isEqualTo(ReactionType.LOVE);
        assertThat(first.count(ReactionType.LOVE)).isEqualTo(1);
        assertThat(first.total()).isEqualTo(1);

        ReactionSummary second = commentReactions.toggle(viewer.getId(), comment.getId(), ReactionType.LOVE);
        assertThat(second.mine()).isNull();
        assertThat(second.count(ReactionType.LOVE)).isEqualTo(0);
        assertThat(second.total()).isEqualTo(0);
    }

    @Test
    void reactingWithADifferentTypeReplacesTheFirstOne() {
        User author = createUser();
        Post post = publicPost(author);
        User commenter = createUser();
        Comment comment = comments.create(commenter.getId(), post.getId(), null, "bình luận test");
        User viewer = createUser();

        commentReactions.toggle(viewer.getId(), comment.getId(), ReactionType.LOVE);
        ReactionSummary result = commentReactions.toggle(viewer.getId(), comment.getId(), ReactionType.HAHA);

        assertThat(result.mine()).isEqualTo(ReactionType.HAHA);
        assertThat(result.count(ReactionType.LOVE)).isEqualTo(0);
        assertThat(result.count(ReactionType.HAHA)).isEqualTo(1);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void notifiesCommentAuthorWhenAnotherUserReacts() {
        User author = createUser();
        Post post = publicPost(author);
        User commenter = createUser();
        Comment comment = comments.create(commenter.getId(), post.getId(), null, "bình luận test");
        User reactor = createUser();

        commentReactions.toggle(reactor.getId(), comment.getId(), ReactionType.FIRE);

        List<NotificationView> list = notifications.listRecent(commenter.getId());
        assertThat(list).anySatisfy(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.COMMENT_REACTION);
            assertThat(n.actor().id()).isEqualTo(reactor.getId());
            assertThat(n.text()).contains("đã bày tỏ cảm xúc về bình luận của bạn");
        });
    }

    @Test
    void doesNotNotifyWhenReactingToOwnComment() {
        User author = createUser();
        Post post = publicPost(author);
        Comment comment = comments.create(author.getId(), post.getId(), null, "tự bình luận");

        commentReactions.toggle(author.getId(), comment.getId(), ReactionType.LOVE);

        List<NotificationView> list = notifications.listRecent(author.getId());
        assertThat(list).noneMatch(n -> n.type() == NotificationType.COMMENT_REACTION);
    }

    @Test
    void reactorsReturnsListOfReactorsForComment() {
        User author = createUser();
        Post post = publicPost(author);
        User commenter = createUser();
        Comment comment = comments.create(commenter.getId(), post.getId(), null, "bình luận test");
        User reactor = createUser();

        commentReactions.toggle(reactor.getId(), comment.getId(), ReactionType.WOW);

        List<Reactor> reactors = commentReactions.reactors(comment.getId(), author.getId());
        assertThat(reactors).hasSize(1);
        assertThat(reactors.get(0).user().id()).isEqualTo(reactor.getId());
        assertThat(reactors.get(0).type()).isEqualTo(ReactionType.WOW);
    }
}
