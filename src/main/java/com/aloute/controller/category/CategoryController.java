package com.aloute.controller.category;

import com.aloute.dto.category.CategoryView;
import com.aloute.exception.category.InvalidCategoryException;
import com.aloute.service.category.CategoryService;

import com.aloute.service.feed.FeedService;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Trang bài theo danh mục (mọi người) và trang quản lý danh mục (Manager, chặn ở SecurityConfig theo {@code /manage/**}). */
@Controller
public class CategoryController {

    private final CategoryService categories;
    private final FeedService feed;

    public CategoryController(CategoryService categories, FeedService feed) {
        this.categories = categories;
        this.feed = feed;
    }

    @GetMapping("/categories/{slug}")
    public String show(@PathVariable String slug, @AuthenticationPrincipal AlouteUserPrincipal viewer, Model model) {
        CategoryView category = categories.bySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("category", category);
        model.addAttribute("page", feed.byCategory(category.id(), viewer == null ? null : viewer.id(), null));
        model.addAttribute("moreUrl", "/categories/" + category.slug() + "/posts");
        return "category/show";
    }

    @GetMapping("/manage/categories")
    public String manage(Model model) {
        model.addAttribute("active", "manage");
        model.addAttribute("categories", categories.all());
        model.addAttribute("maxName", CategoryService.MAX_NAME);
        return "manage/categories";
    }

    @PostMapping("/manage/categories")
    public String create(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String name, RedirectAttributes flash) {
        return run(flash, "Đã thêm danh mục.", () -> categories.create(me.id(), name));
    }

    @PostMapping("/manage/categories/{id}/rename")
    public String rename(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                         @RequestParam String name, RedirectAttributes flash) {
        return run(flash, "Đã đổi tên danh mục.", () -> categories.rename(me.id(), id, name));
    }

    @PostMapping("/manage/categories/{id}/active")
    public String setActive(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                            @RequestParam boolean active, RedirectAttributes flash) {
        return run(flash, active ? "Đã bật lại danh mục." : "Đã ngưng dùng danh mục.", () -> categories.setActive(me.id(), id, active));
    }

    private static String run(RedirectAttributes flash, String success, Runnable action) {
        try {
            action.run();
            flash.addFlashAttribute("notice", success);
        } catch (InvalidCategoryException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manage/categories";
    }
}
