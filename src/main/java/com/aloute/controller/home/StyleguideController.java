package com.aloute.controller.home;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Trang duyệt design system, chỉ tồn tại khi chạy profile dev. */
@Controller
@Profile("dev")
public class StyleguideController {

    @GetMapping("/dev/styleguide")
    public String styleguide() {
        return "dev/styleguide";
    }
}
