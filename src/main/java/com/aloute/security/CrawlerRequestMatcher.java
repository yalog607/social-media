package com.aloute.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Nhận diện bot tạo bản xem trước liên kết (Facebook, Zalo, Discord...) gọi GET vào trang bài, hồ sơ hoặc hashtag.
 * Chỉ dùng để cho bot đọc thẻ Open Graph khi chưa đăng nhập; người thật vẫn phải đăng nhập.
 */
public class CrawlerRequestMatcher implements RequestMatcher {

    static final Pattern BOT_AGENT = Pattern.compile(
            "facebookexternalhit|facebot|zalo|twitterbot|discordbot|slackbot|telegrambot|linkedinbot|whatsapp"
                    + "|skypeuripreview|pinterest|embedly",
            Pattern.CASE_INSENSITIVE);

    private final List<RequestMatcher> paths = List.of(
            new AntPathRequestMatcher("/posts/{id}", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/u/{username}", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/tags/{tag}", HttpMethod.GET.name()));

    @Override
    public boolean matches(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        return agent != null && BOT_AGENT.matcher(agent).find() && paths.stream().anyMatch(p -> p.matches(request));
    }
}
