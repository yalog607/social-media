package com.aloute.home;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** Khách thấy trang giới thiệu; người đã đăng nhập thấy khung bảng tin (nội dung thật ở GĐ2). */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal AlouteUserPrincipal principal) {
        return principal == null ? "home/landing" : "home/feed";
    }
}
