package com.aloute.user;

import com.aloute.social.BlockService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
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

    /**
     * Từng có lỗi: {@code th:if}/{@code th:unless} trên hai thẻ khác nhau cùng gắn {@code th:replace} tới
     * fragment avatar khiến CẢ HAI đều render, hiện 2 ảnh đại diện chồng nhau. Đếm số lần fragment avatar
     * xuất hiện để không tái diễn dù nguyên nhân gốc là gì.
     */
    @Test
    void profilePageRendersTheAvatarFragmentExactlyOnce() throws Exception {
        User owner = withPhotos(true);

        String body = mvc.perform(get("/u/" + owner.getUsername()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(countOccurrences(body, "class=\"avatar avatar--xl\"")).isEqualTo(1);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
