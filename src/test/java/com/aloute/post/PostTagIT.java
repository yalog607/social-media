package com.aloute.post;

import com.aloute.dto.post.PostView;
import com.aloute.exception.post.InvalidPostException;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.service.post.PostViewAssembler;

import com.aloute.service.notification.NotificationService;
import com.aloute.service.social.FriendService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
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

    @Test
    void editingReplacesTagsNotifiesOnlyNewPeopleAndNullKeepsThem() {
        User author = createUser();
        User a = createUser();
        User b = createUser();
        makeFriends(author, a);
        makeFriends(author, b);
        Post post = posts.create(author.getId(), "đi chơi", Visibility.PUBLIC, List.of(), null, List.of(a.getId()));

        posts.edit(author.getId(), post.getId(), "đi chơi", null, null, null);
        assertThat(assembler.assemble(List.of(post), author.getId()).get(0).tagged()).extracting(PostView.AuthorView::id)
                .as("null = giữ nguyên").containsExactly(a.getId());

        posts.edit(author.getId(), post.getId(), "đi chơi", null, List.of(a.getId(), b.getId()), null);
        assertThat(assembler.assemble(List.of(post), author.getId()).get(0).tagged()).extracting(PostView.AuthorView::id)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(notifications.listRecent(a.getId())).filteredOn(n -> n.text().contains("gắn thẻ")).as("không báo lại người cũ").hasSize(1);
        assertThat(notifications.listRecent(b.getId())).filteredOn(n -> n.text().contains("gắn thẻ")).hasSize(1);

        posts.edit(author.getId(), post.getId(), "đi chơi", null, List.of(b.getId()), null);
        assertThat(assembler.assemble(List.of(post), author.getId()).get(0).tagged()).extracting(PostView.AuthorView::id)
                .containsExactly(b.getId());

        posts.edit(author.getId(), post.getId(), "đi chơi", null, List.of(), null);
        assertThat(assembler.assemble(List.of(post), author.getId()).get(0).tagged()).isEmpty();
    }

    @Test
    void editingTagsFollowsTheSameRulesAsCreating() {
        User author = createUser();
        User friend = createUser();
        User stranger = createUser();
        makeFriends(author, friend);
        Post post = posts.create(author.getId(), "x", Visibility.PUBLIC, List.of(), null);

        assertThatThrownBy(() -> posts.edit(author.getId(), post.getId(), "x", null, List.of(stranger.getId()), null))
                .isInstanceOf(InvalidPostException.class);
        posts.edit(author.getId(), post.getId(), "x", null, List.of(friend.getId()), null);

        posts.edit(author.getId(), post.getId(), "x", Visibility.PRIVATE, List.of(friend.getId()), null);
        assertThat(assembler.assemble(List.of(post), author.getId()).get(0).tagged()).as("chuyển riêng tư thì gỡ hết thẻ").isEmpty();
    }
}
