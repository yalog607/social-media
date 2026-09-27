package com.aloute.user;

import com.aloute.social.BlockService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phóng to ảnh đại diện/ảnh bìa: luôn cho chủ hồ sơ, cho người khác chỉ khi chủ hồ sơ bật và không bị chặn. */
class ProfilePhotoZoomIT extends IntegrationTest {

    @Autowired BlockService blocks;

    private User withPhotos(boolean zoomEnabled) {
        User user = createUser();
        user.getProfile().setAvatarUrl("/uploads/avatars/a.jpg");
        user.getProfile().setCoverUrl("/uploads/covers/c.jpg");
        user.getProfile().setPhotoZoomEnabled(zoomEnabled);
        users.saveAndFlush(user);
        return user;
    }

    @Test
    void ownerCanAlwaysZoomEvenWithSettingOff() throws Exception {
        User owner = withPhotos(false);

        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-lightbox=\"/uploads/avatars/a.jpg\"")))
                .andExpect(content().string(containsString("data-lightbox=\"/uploads/covers/c.jpg\"")));
    }

    @Test
    void strangerCanZoomWhenOwnerAllowsIt() throws Exception {
        User owner = withPhotos(true);

        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-lightbox=\"/uploads/avatars/a.jpg\"")))
                .andExpect(content().string(containsString("data-lightbox=\"/uploads/covers/c.jpg\"")));
    }

    @Test
    void strangerCannotZoomWhenOwnerDisallowsIt() throws Exception {
        User owner = withPhotos(false);

        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-lightbox"))));
    }

    @Test
    void blockedViewerCannotZoomEvenIfOwnerAllowsIt() throws Exception {
        User owner = withPhotos(true);
        User stranger = createUser();
        blocks.block(owner.getId(), stranger.getId());

        // hồ sơ giờ không xem được nữa (canView=false do bị chặn) nên chắc chắn không có ảnh bìa/nút phóng to nào
        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(stranger)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-lightbox"))));
    }
}
