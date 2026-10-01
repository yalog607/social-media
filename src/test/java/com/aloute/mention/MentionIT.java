package com.aloute.mention;

import com.aloute.comment.CommentService;
import com.aloute.notification.NotificationService;
import com.aloute.post.Mentions;
import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.post.PostTextRenderer;
import com.aloute.social.BlockService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Nhắc tên @username: nhận diện, hiển thị liên kết, và thông báo trong bình luận. */
class MentionIT extends IntegrationTest {

    @Autowired CommentService comments;
    @Autowired PostService posts;
    @Autowired NotificationService notifications;
    @Autowired BlockService blocks;

    private long mentionNotes(User user) {
        return notifications.listRecent(user.getId()).stream().filter(n -> n.text().contains("nhắc đến bạn")).count();
    }

    @Test
    void extractsUsernamesIgnoringEmailsAndTrailingDots() {
        assertThat(Mentions.extract("chào @Mochi.xinh, và @bob_99. cảm ơn @ab nhé, mail a@example.com")).containsExactly("mochi.xinh", "bob_99");
        assertThat(Mentions.extract("@@abc và &@xyz1 và x@yyy")).isEmpty();
        assertThat(Mentions.extract("@aaa @AAA @aaa")).containsExactly("aaa");
        assertThat(Mentions.extract(null)).isEmpty();
    }

    @Test
    void rendersMentionsAsEscapedProfileLinks() {
        String html = PostTextRenderer.toSafeHtml("hi @mochi_1. <b>x</b> @a<script>");

        assertThat(html).contains("<a class=\"mention\" href=\"/u/mochi_1\">@mochi_1</a>.");
        assertThat(html).contains("&lt;b&gt;").doesNotContain("<b>").doesNotContain("<script>");
    }

    @Test
    void commentMentionNotifiesVisibleUsersOnceAndSkipsSelfBlockedAndAlreadyNotified() {
        User author = createUser();
        User postOwner = createUser();
        User mentioned = createUser();
        User blocked = createUser();
        blocks.block(blocked.getId(), author.getId());
        Post post = posts.create(postOwner.getId(), "bài", Visibility.PUBLIC, List.of(), null);

        comments.create(author.getId(), post.getId(), null,
                "cc @" + mentioned.getUsername() + " @" + mentioned.getUsername() + " @" + author.getUsername()
                        + " @" + postOwner.getUsername() + " @" + blocked.getUsername());

        assertThat(mentionNotes(mentioned)).isEqualTo(1);
        assertThat(mentionNotes(author)).as("không tự nhắc mình").isZero();
        assertThat(mentionNotes(postOwner)).as("chủ bài đã nhận thông báo bình luận").isZero();
        assertThat(mentionNotes(blocked)).isZero();
    }

    @Test
    void doesNotNotifyPeopleWhoCannotSeeThePost() {
        User author = createUser();
        User outsider = createUser();
        Post post = posts.create(author.getId(), "chỉ bạn bè", Visibility.FRIENDS, List.of(), null);

        comments.create(author.getId(), post.getId(), null, "@" + outsider.getUsername());

        assertThat(mentionNotes(outsider)).isZero();
    }

    @Test
    void unknownUsernamesAreIgnored() {
        User author = createUser();
        Post post = posts.create(author.getId(), "bài", Visibility.PUBLIC, List.of(), null);

        comments.create(author.getId(), post.getId(), null, "@khong_ton_tai_hoan_toan");

        assertThat(Set.of(1)).hasSize(1);
    }
}
