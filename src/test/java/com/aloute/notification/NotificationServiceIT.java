package com.aloute.notification;

import com.aloute.comment.Comment;
import com.aloute.comment.CommentService;
import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.reaction.ReactionService;
import com.aloute.reaction.ReactionType;
import com.aloute.social.FollowService;
import com.aloute.social.FriendService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Phần 3b: thông báo trong ứng dụng, phát sinh từ các thao tác xã hội và bài viết đã có. */
class NotificationServiceIT extends IntegrationTest {

    @Autowired NotificationService notifications;
    @Autowired FriendService friends;
    @Autowired FollowService follows;
    @Autowired ReactionService reactions;
    @Autowired CommentService comments;
    @Autowired PostService posts;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài của " + author.getUsername(), Visibility.PUBLIC, List.of(), null);
    }

    @Test
    void sendingAFriendRequestNotifiesTheTarget() {
        User a = createUser();
        User b = createUser();

        friends.sendRequest(a.getId(), b.getId());

        NotificationView view = onlyNotificationFor(b.getId());
        assertThat(view.actor().id()).isEqualTo(a.getId());
        assertThat(view.text()).contains("lời mời kết bạn");
        assertThat(notifications.unreadCount(a.getId())).as("người gửi không tự thông báo cho mình").isZero();
    }

    @Test
    void acceptingCrossedRequestsNotifiesTheOriginalRequester() {
        User a = createUser();
        User b = createUser();
        friends.sendRequest(a.getId(), b.getId());

        friends.sendRequest(b.getId(), a.getId()); // b mời lại a: coi như b vừa chấp nhận lời mời của a

        NotificationView view = onlyNotificationFor(a.getId());
        assertThat(view.actor().id()).isEqualTo(b.getId());
        assertThat(view.text()).contains("chấp nhận");
    }

    @Test
    void explicitAcceptNotifiesTheRequester() {
        User a = createUser();
        User b = createUser();
        friends.sendRequest(a.getId(), b.getId());

        friends.accept(b.getId(), a.getId());

        NotificationView view = onlyNotificationFor(a.getId());
        assertThat(view.actor().id()).isEqualTo(b.getId());
        assertThat(view.text()).contains("chấp nhận");
    }

    @Test
    void followingNotifiesTheFollowee() {
        User a = createUser();
        User b = createUser();

        follows.follow(a.getId(), b.getId());

        assertThat(onlyNotificationFor(b.getId()).text()).contains("theo dõi");
    }

    @Test
    void reactingNotifiesThePostAuthorButNotOnUnreact() {
        User author = createUser();
        Post post = publicPost(author);
        User fan = createUser();

        reactions.toggle(fan.getId(), post.getId(), ReactionType.LOVE);
        assertThat(onlyNotificationFor(author.getId()).text()).contains("cảm xúc");

        reactions.toggle(fan.getId(), post.getId(), ReactionType.LOVE); // bỏ cảm xúc: không thêm thông báo mới
        assertThat(notifications.listRecent(author.getId())).hasSize(1);
    }

    @Test
    void reactingToYourOwnPostDoesNotNotifyYourself() {
        User author = createUser();
        Post post = publicPost(author);

        reactions.toggle(author.getId(), post.getId(), ReactionType.HAHA);

        assertThat(notifications.unreadCount(author.getId())).isZero();
    }

    @Test
    void rootCommentNotifiesThePostAuthor() {
        User author = createUser();
        Post post = publicPost(author);
        User commenter = createUser();

        comments.create(commenter.getId(), post.getId(), null, "Hay ghê!");

        assertThat(onlyNotificationFor(author.getId()).text()).contains("bình luận");
    }

    @Test
    void replyNotifiesTheRootCommentAuthorNotThePostAuthor() {
        User author = createUser();
        Post post = publicPost(author);
        User rootCommenter = createUser();
        User replier = createUser();
        Comment root = comments.create(rootCommenter.getId(), post.getId(), null, "gốc");

        comments.create(replier.getId(), post.getId(), root.getId(), "trả lời");

        assertThat(onlyNotificationFor(rootCommenter.getId()).text()).contains("trả lời");
        assertThat(notifications.unreadCount(author.getId()))
                .as("tác giả bài chỉ nhận đúng 1 thông báo (từ bình luận gốc), trả lời không thông báo thêm cho tác giả bài")
                .isEqualTo(1);
    }

    @Test
    void sharingNotifiesTheOriginalAuthor() {
        User author = createUser();
        Post post = publicPost(author);
        User sharer = createUser();

        posts.share(sharer.getId(), post.getId(), "xem cái này");

        assertThat(onlyNotificationFor(author.getId()).text()).contains("chia sẻ");
    }

    @Test
    void listRecentReflectsReadStateBeforeMarkAllReadThenClearsUnreadCount() {
        User author = createUser();
        Post post = publicPost(author);
        follows.follow(createUser().getId(), author.getId());
        reactions.toggle(createUser().getId(), post.getId(), ReactionType.WOW);

        assertThat(notifications.unreadCount(author.getId())).isEqualTo(2);
        assertThat(notifications.listRecent(author.getId())).allMatch(v -> !v.read());

        notifications.markAllRead(author.getId());

        assertThat(notifications.unreadCount(author.getId())).isZero();
        assertThat(notifications.listRecent(author.getId())).allMatch(NotificationView::read);
    }

    private NotificationView onlyNotificationFor(java.util.UUID userId) {
        List<NotificationView> views = notifications.listRecent(userId);
        assertThat(views).hasSize(1);
        return views.get(0);
    }
}
