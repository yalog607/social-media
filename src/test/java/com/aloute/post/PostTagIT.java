package com.aloute.post;

import com.aloute.notification.NotificationService;
import com.aloute.social.FriendService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gắn thẻ bạn bè khi đăng bài: chỉ bạn bè, có giới hạn, có thông báo và hiện trong PostView. */
class PostTagIT extends IntegrationTest {

    @Autowired PostService posts;
    @Autowired PostViewAssembler assembler;
    @Autowired FriendService friends;
    @Autowired NotificationService notifications;

    private void makeFriends(User a, User b) {
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
    }

    @Test
    void taggingAFriendShowsThemOnThePostAndNotifiesThem() {
        User author = createUser();
        User friend = createUser();
        makeFriends(author, friend);

        Post post = posts.create(author.getId(), "đi chơi", Visibility.PUBLIC, List.of(), null, List.of(friend.getId()));

        PostView view = assembler.assemble(List.of(post), author.getId()).get(0);
        assertThat(view.tagged()).extracting(PostView.AuthorView::id).containsExactly(friend.getId());
        assertThat(notifications.listRecent(friend.getId()))
                .anyMatch(n -> n.text().contains("gắn thẻ bạn") && post.getId().equals(n.postId()));
    }

    @Test
    void cannotTagSomeoneWhoIsNotAFriend() {
        User author = createUser();
        User stranger = createUser();

        assertThatThrownBy(() -> posts.create(author.getId(), "x", Visibility.PUBLIC, List.of(), null, List.of(stranger.getId())))
                .isInstanceOf(InvalidPostException.class);
    }

    @Test
    void cannotTagOnAPrivatePost() {
        User author = createUser();
        User friend = createUser();
        makeFriends(author, friend);

        assertThatThrownBy(() -> posts.create(author.getId(), "x", Visibility.PRIVATE, List.of(), null, List.of(friend.getId())))
                .isInstanceOf(InvalidPostException.class);
    }

    @Test
    void duplicatesAndSelfAreIgnoredButTooManyTagsAreRejected() {
        User author = createUser();
        User friend = createUser();
        makeFriends(author, friend);

        Post post = posts.create(author.getId(), "x", Visibility.PUBLIC, List.of(), null,
                List.of(friend.getId(), friend.getId(), author.getId()));
        assertThat(assembler.assemble(List.of(post), author.getId()).get(0).tagged()).hasSize(1);

        List<UUID> tooMany = java.util.stream.IntStream.range(0, PostService.MAX_TAGS + 1)
                .mapToObj(i -> UUID.randomUUID()).toList();
        assertThatThrownBy(() -> posts.create(author.getId(), "x", Visibility.PUBLIC, List.of(), null, tooMany))
                .isInstanceOf(InvalidPostException.class);
    }
}
