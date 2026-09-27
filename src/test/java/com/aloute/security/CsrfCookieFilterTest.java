package com.aloute.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Một lần tải trang kéo theo hàng chục request tài nguyên tĩnh chạy song song; nếu mỗi request đó cũng ép
 * sinh cookie CSRF, chúng đua nhau ghi đè và cookie cuối cùng trong trình duyệt có thể khác giá trị đã in
 * sẵn vào form của trang — form vừa tải xong bị 403. Test này chốt danh sách đường dẫn được bỏ qua.
 */
class CsrfCookieFilterTest {

    private static boolean shouldNotFilter(String path) throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getServletPath()).thenReturn(path);
        Method method = CsrfCookieFilter.class.getDeclaredMethod("shouldNotFilter", HttpServletRequest.class);
        method.setAccessible(true);
        return (boolean) method.invoke(new CsrfCookieFilter(), request);
    }

    @ParameterizedTest
    @CsvSource({
            "/css/aloute.css, true",
            "/js/feed.js, true",
            "/fonts/be-vietnam-pro.woff2, true",
            "/webjars/bootstrap/5.3.8/css/bootstrap.min.css, true",
            "/img/logo.svg, true",
            "/uploads/posts/a.jpg, true",
            "/favicon.ico, true",
            "/, false",
            "/login, false",
            "/posts/123, false",
            "/api/posts/123/comments, false",
    })
    void skipsOnlyStaticResourcePaths(String path, boolean expected) throws Exception {
        assertThat(shouldNotFilter(path)).isEqualTo(expected);
    }
}
