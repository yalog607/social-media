package com.aloute.home;

import com.aloute.feed.FeedService;
import com.aloute.media.MediaLimits;
import com.aloute.post.Post;
import com.aloute.search.SearchService;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class HomeController {

    private final FeedService feed;
    private final SearchService search;

    public HomeController(FeedService feed, SearchService search) {
        this.feed = feed;
        this.search = search;
    }

    /** Khách thấy trang giới thiệu; người đã đăng nhập thấy bảng tin (trang đầu, các trang sau tải bằng /feed). */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal AlouteUserPrincipal principal, Model model) {
        if (principal == null) {
            return "home/landing";
        }
        model.addAttribute("page", feed.home(principal.id(), null));
        model.addAttribute("moreUrl", "/feed");
        model.addAttribute("trending", search.trending());
        // Cùng con số với MediaLimits/Post để trình duyệt kiểm tra trước khi tải lên
        model.addAttribute("mediaLimits", Map.of(
                "maxImages", MediaLimits.MAX_IMAGES,
                "maxImageMb", MediaLimits.MAX_IMAGE_BYTES / (1024 * 1024),
                "maxVideoMb", MediaLimits.MAX_VIDEO_BYTES / (1024 * 1024),
                "maxChars", Post.MAX_CONTENT_LENGTH));
        return "home/feed";
    }
}
