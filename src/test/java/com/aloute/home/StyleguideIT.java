package com.aloute.home;

import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Với profile dev, trang styleguide phải render được toàn bộ thành phần.
 * (Việc nó KHÔNG tồn tại ở profile thường được kiểm tra trong RoleAccessIT.)
 */
@ActiveProfiles({"dev", "test"})
class StyleguideIT extends IntegrationTest {

    @Test
    void rendersEveryComponentWithoutTemplateErrors() throws Exception {
        mvc.perform(get("/dev/styleguide"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Styleguide")))
                .andExpect(content().string(containsString("btn-al--pink")))
                .andExpect(content().string(containsString("var(--lavender)")));
    }
}
