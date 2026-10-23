package com.aloute.controller.search;

import com.aloute.service.search.SearchService;

import com.aloute.util.common.TextNormalizer;
import com.aloute.service.feed.FeedService;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Trang tìm kiếm (người dùng, bài viết, hashtag) và trang một hashtag. Khách dùng được vì chỉ thấy nội dung công khai. */
@Controller
public class SearchController {

    private final SearchService search;
    private final FeedService feed;

    public SearchController(SearchService search, FeedService feed) {
        this.search = search;
        this.feed = feed;
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q, @RequestParam(defaultValue = "posts") String tab,
                         @AuthenticationPrincipal AlouteUserPrincipal viewer, Model model) {
        String query = q == null ? "" : q.trim();
        // Gõ #tag vào ô tìm kiếm thì đi thẳng tới trang hashtag, không hiện 3 tab
        String normalizedTag = TextNormalizer.forSearch(query.replaceFirst("^#", ""));
        if (query.startsWith("#") && !normalizedTag.isEmpty()) {
            return "redirect:/tags/" + UriUtils.encodePathSegment(normalizedTag, StandardCharsets.UTF_8);
        }

        UUID viewerId = viewer == null ? null : viewer.id();
        model.addAttribute("q", query);
        model.addAttribute("tab", tab);
        model.addAttribute("tooShort", !query.isEmpty() && !SearchService.isValidQuery(TextNormalizer.forSearch(query)));
        switch (tab) {
            case "users" -> model.addAttribute("users", search.searchUsers(query));
            case "hashtags" -> model.addAttribute("hashtags", search.searchHashtags(query));
            default -> model.addAttribute("posts", search.searchPosts(query, viewerId));
        }
        return "search/results";
    }

    @GetMapping("/tags/{tag}")
    public String tag(@PathVariable String tag, @AuthenticationPrincipal AlouteUserPrincipal viewer, Model model) {
        String normalized = TextNormalizer.forSearch(tag);
        UUID viewerId = viewer == null ? null : viewer.id();
        model.addAttribute("tag", normalized);
        model.addAttribute("page", feed.byHashtag(normalized, viewerId, null));
        model.addAttribute("moreUrl", "/tags/" + normalized + "/posts");
        return "tags/show";
    }
}
