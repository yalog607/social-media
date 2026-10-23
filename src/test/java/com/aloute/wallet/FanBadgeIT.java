package com.aloute.wallet;

import com.aloute.dto.wallet.FanView;
import com.aloute.model.wallet.FanBadge;
import com.aloute.service.wallet.FanService;
import com.aloute.service.wallet.WalletService;

import com.aloute.service.comment.CommentService;
import com.aloute.dto.comment.CommentView;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Huy hiệu fan: ngưỡng, hiện ở bình luận của fan trên bài của Creator, và bảng xếp hạng. */
class FanBadgeIT extends IntegrationTest {

    @Autowired WalletService wallet;
    @Autowired FanService fans;
    @Autowired PostService posts;
    @Autowired CommentService comments;

    @Test
    void thresholdsPickTheHighestReachedBadge() {
        assertThat(FanBadge.forTotal(9)).isEmpty();
        assertThat(FanBadge.forTotal(10)).contains(FanBadge.BRONZE);
        assertThat(FanBadge.forTotal(49)).contains(FanBadge.BRONZE);
        assertThat(FanBadge.forTotal(50)).contains(FanBadge.SILVER);
        assertThat(FanBadge.forTotal(200)).contains(FanBadge.GOLD);
        assertThat(FanBadge.forTotal(10_000)).contains(FanBadge.GOLD);
    }

    @Test
    void badgeGrowsWithDonationsAndNeverShrinks() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();

        wallet.donate(fan.getId(), creator.getId(), 5);
        assertThat(fans.badgesFor(creator.getId(), List.of(fan.getId()))).isEmpty();

        wallet.donate(fan.getId(), creator.getId(), 5);
        assertThat(fans.badgesFor(creator.getId(), List.of(fan.getId()))).containsEntry(fan.getId(), FanBadge.BRONZE);

        wallet.donate(fan.getId(), creator.getId(), 40);
        assertThat(fans.badgesFor(creator.getId(), List.of(fan.getId()))).containsEntry(fan.getId(), FanBadge.SILVER);
    }

    @Test
    void badgesAreSpecificToEachCreatorAndFansAreRanked() {
        User creatorA = createUser(Role.CREATOR);
        User creatorB = createUser(Role.CREATOR);
        User big = createUser();
        User small = createUser();
        wallet.donate(big.getId(), creatorA.getId(), 60);
        wallet.donate(small.getId(), creatorA.getId(), 10);

        assertThat(fans.badgesFor(creatorB.getId(), List.of(big.getId(), small.getId()))).isEmpty();
        assertThat(fans.topFans(creatorA.getId())).extracting(FanView::userId).containsExactly(big.getId(), small.getId());
        assertThat(fans.topFans(creatorA.getId()).get(0).badge()).isEqualTo(FanBadge.SILVER);
    }

    @Test
    void fanCommentOnTheCreatorsPostShowsTheBadge() throws Exception {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        User stranger = createUser();
        Post post = posts.create(creator.getId(), "bài", Visibility.PUBLIC, List.of(), null);
        wallet.donate(fan.getId(), creator.getId(), 20);
        comments.create(fan.getId(), post.getId(), null, "ủng hộ");
        comments.create(stranger.getId(), post.getId(), null, "xin chào");

        List<CommentView> views = comments.list(post.getId(), creator.getId());

        assertThat(views).filteredOn(c -> c.author().id().equals(fan.getId())).singleElement()
                .satisfies(c -> assertThat(c.badge()).isEqualTo(FanBadge.BRONZE));
        assertThat(views).filteredOn(c -> c.author().id().equals(stranger.getId())).singleElement()
                .satisfies(c -> assertThat(c.badge()).isNull());
        assertThat(Optional.ofNullable(views.get(0).badge())).isNotNull();
        mvc.perform(get("/creator/fans").with(asUser(creator))).andExpect(status().isOk());
        mvc.perform(get("/creator/fans").with(asUser(fan))).andExpect(status().isForbidden());
    }
}
