package com.aloute.common;

import com.aloute.support.IntegrationTest;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * File mẫu lỗi phải nằm ở {@code templates/error.html} (tên view Spring Boot mặc định tra là "error"),
 * không phải {@code templates/error/error.html} — sai chỗ thì mọi lỗi không tự viết tay (404 do URL không
 * khớp handler nào, 403 từ Spring Security) đều rơi về trang Whitelabel xấu xí thay vì trang lỗi riêng của app.
 * <p>
 * MockMvc không tự "forward" sang {@code /error} như một container thật khi gặp {@code sendError()}, nên test
 * gọi thẳng {@code GET /error} kèm các request attribute mà container sẽ tự đặt trước khi forward — đúng cách
 * Spring Boot khuyên dùng để kiểm tra trang lỗi tùy biến.
 */
class ErrorPageIT extends IntegrationTest {

    @Test
    void notFoundShowsTheCustomErrorPageNotWhitelabel() throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .with(asUser(createUser()))
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/duong-dan-khong-ton-tai"))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))))
                .andExpect(content().string(containsString("Trang này đi lạc rồi")))
                .andExpect(content().string(not(containsString("alert-al--error"))));
    }

    @Test
    void forbiddenShowsTheCustomErrorPageNotWhitelabel() throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin"))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))))
                .andExpect(content().string(containsString("Khu vực này chưa mở cho bạn")));
    }
}
