package com.aloute.user;

import com.aloute.model.user.User;
import com.aloute.service.user.AvatarFrameService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class AvatarFrameIT extends IntegrationTest {
    @Autowired AvatarFrameService frames;

    @Test
    void freeFramesCanBeSelectedAndClearedAndHiddenFramesRequireGrant() {
        User user = createUser();
        assertThat(users.findById(user.getId()).orElseThrow().getProfile().getAvatarFrame()).isNull();
        assertThat(frames.available(user.getId())).extracting(AvatarFrameService.Frame::code)
                .containsExactlyInAnyOrder("pink", "lavender", "sunshine");
        assertThatThrownBy(() -> frames.select(user.getId(), "event-star"))
                .isInstanceOf(IllegalArgumentException.class);
        frames.select(user.getId(), "pink");
        assertThat(users.findById(user.getId()).orElseThrow().getProfile().getAvatarFrame()).isEqualTo("pink");
        frames.grant(user.getId(), "event-star");
        frames.grant(user.getId(), "event-star");
        assertThat(frames.available(user.getId())).extracting(AvatarFrameService.Frame::code).contains("event-star");
        frames.select(user.getId(), "event-star");
        assertThat(users.findById(user.getId()).orElseThrow().getProfile().getAvatarFrame()).isEqualTo("event-star");
        frames.select(user.getId(), "");
        assertThat(users.findById(user.getId()).orElseThrow().getProfile().getAvatarFrame()).isNull();
    }
}
