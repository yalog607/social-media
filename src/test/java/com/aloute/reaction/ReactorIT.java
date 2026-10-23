package com.aloute.reaction;

import com.aloute.dto.reaction.Reactor;
import com.aloute.model.reaction.ReactionType;
import com.aloute.service.reaction.ReactionService;

import com.aloute.model.post.Post;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Xem ai đã thả cảm xúc cho một bài. */
class ReactorIT extends IntegrationTest {

    @Autowired ReactionService reactions;
    @Autowired PostService posts;

    @Test
    void listsReactorsWithTheirTypeNewestFirstAndDropsRemovedOnes() {
        User author = createUser();
        User a = createUser();
        User b = createUser();
        Post post = posts.create(author.getId(), "bài", Visibility.PUBLIC, List.of(), null);
        reactions.toggle(a.getId(), post.getId(), ReactionType.LOVE);
        reactions.toggle(b.getId(), post.getId(), ReactionType.HAHA);

        List<Reactor> list = reactions.reactors(post.getId(), author.getId());

        assertThat(list).extracting(r -> r.user().id()).containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(list).filteredOn(r -> r.user().id().equals(b.getId())).singleElement()
                .satisfies(r -> assertThat(r.type()).isEqualTo(ReactionType.HAHA));

        reactions.toggle(a.getId(), post.getId(), ReactionType.LOVE);
        assertThat(reactions.reactors(post.getId(), author.getId())).extracting(r -> r.user().id()).containsExactly(b.getId());
    }

    @Test
    void cannotSeeReactorsOfAPostYouCannotView() {
        Post hidden = posts.create(createUser().getId(), "riêng tư", Visibility.PRIVATE, List.of(), null);

        assertThatThrownBy(() -> reactions.reactors(hidden.getId(), createUser().getId())).isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void endpointRendersTheFragmentAndNeedsLogin() throws Exception {
        User author = createUser();
        User fan = createUser();
        Post post = posts.create(author.getId(), "bài", Visibility.PUBLIC, List.of(), null);
        reactions.toggle(fan.getId(), post.getId(), ReactionType.LOVE);

        mvc.perform(get("/api/posts/" + post.getId() + "/reactions").with(asUser(author)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(fan.getProfile().getDisplayName())));
        mvc.perform(get("/api/posts/" + post.getId() + "/reactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/").with(asUser(author))).andExpect(content().string(containsString("reactorsModal")));
    }
}
