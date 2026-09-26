package com.aloute.config;

import com.aloute.security.ActiveAccountInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AlouteProperties props;
    private final ActiveAccountInterceptor activeAccount;

    public WebMvcConfig(AlouteProperties props, ActiveAccountInterceptor activeAccount) {
        this.props = props;
        this.activeAccount = activeAccount;
    }

    /**
     * Phục vụ ảnh tải lên. Tên file là UUID sinh khi lưu và không bao giờ đổi nội dung (ảnh mới = tên mới),
     * nên cho trình duyệt/CDN cache một năm.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(props.storage().localDir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(root.toUri().toString().replaceAll("/?$", "/"))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    /**
     * Chặn tài khoản đã bị khóa/xóa còn token hợp lệ. Bỏ qua các trang xác thực để không tạo vòng chuyển hướng
     * khi cookie cũ còn sót lại, và bỏ qua tài nguyên tĩnh.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activeAccount)
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/register", "/forgot-password", "/reset-password",
                        "/logout", "/auth/**", "/error", "/css/**", "/js/**", "/img/**", "/fonts/**",
                        "/webjars/**", "/uploads/**", "/favicon.ico");
    }
}
