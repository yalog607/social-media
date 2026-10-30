package com.aloute.controller.home;

import com.aloute.dto.chat.Streak;
import com.aloute.dto.mood.FriendVibeView;
import com.aloute.dto.mood.MoodView;
import com.aloute.dto.post.PostView.AuthorView;
import com.aloute.model.post.Post;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.chat.StreakService;
import com.aloute.service.feed.FeedService;
import com.aloute.service.mood.MoodService;
import com.aloute.service.post.PostService;
import com.aloute.service.search.SearchService;
import com.aloute.service.social.FriendService;
import com.aloute.service.story.StoryService;
import com.aloute.service.season.SeasonService;
import com.aloute.util.media.MediaLimits;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
public class HomeController {

    private final FeedService feed;
    private final SearchService search;
    private final FriendService friends;
    private final MoodService moodService;
    private final StreakService streaks;
    private final StoryService storyService;
    private final SeasonService seasons;

    public HomeController(FeedService feed, SearchService search, FriendService friends,
                          MoodService moodService, StreakService streaks, StoryService storyService, SeasonService seasons) {
        this.seasons = seasons;
        this.feed = feed;
        this.search = search;
        this.friends = friends;
        this.moodService = moodService;
        this.streaks = streaks;
        this.storyService = storyService;
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
        var featuredSeason = seasons.featured();
        model.addAttribute("featuredSeason", featuredSeason);
        model.addAttribute("featuredStandings", featuredSeason == null ? List.of() : seasons.standings(featuredSeason.id()).stream().limit(5).toList());

        List<AuthorView> friendList = friends.friendsOf(principal.id());
        model.addAttribute("tagFriends", friendList);
        model.addAttribute("maxTags", PostService.MAX_TAGS);

        // Khoảnh khắc 24H (Story Tray)
        model.addAttribute("storyTray", storyService.getStoryTray(principal.id()));

        // Tâm trạng của bạn
        model.addAttribute("myMood", moodService.getMyMood(principal.id()).orElse(null));
        model.addAttribute("moodPresets", moodService.getPresets());

        // Chuỗi nhắn tin với bạn bè (tính từ các tin nhắn trực tiếp 1-1)
        List<UUID> friendIds = friendList.stream().map(AuthorView::id).toList();
        Map<UUID, Streak> friendStreaks = streaks.forUserAndFriends(principal.id(), friendIds);

        // Danh sách vibe hôm nay của bạn bè
        List<MoodView> friendsMoods = moodService.getFriendsMoods(principal.id());

        // Tập hợp danh sách bạn bè hiển thị ở thanh bên: TẤT CẢ BẠN BÈ!
        List<FriendVibeView> friendVibes = new ArrayList<>();
        Set<UUID> addedUserIds = new HashSet<>();

        // 1. Thêm bạn bè đã đặt tâm trạng (kèm chuỗi nhắn tin nếu có)
        for (MoodView m : friendsMoods) {
            Streak st = friendStreaks.getOrDefault(m.user().id(), Streak.NONE);
            friendVibes.add(new FriendVibeView(m.user(), m, st));
            addedUserIds.add(m.user().id());
        }

        // 2. Thêm TẤT CẢ các bạn bè còn lại (kèm chuỗi nhắn tin nếu có)
        for (AuthorView f : friendList) {
            if (!addedUserIds.contains(f.id())) {
                Streak st = friendStreaks.getOrDefault(f.id(), Streak.NONE);
                friendVibes.add(new FriendVibeView(f, null, st));
                addedUserIds.add(f.id());
            }
        }

        // Sắp xếp ưu tiên: Bạn bè có chuỗi nhắn tin (streak cao nhất) lên đầu tiên!
        friendVibes.sort((a, b) -> {
            int aStreak = a.hasStreak() ? a.chatStreak().days() : 0;
            int bStreak = b.hasStreak() ? b.chatStreak().days() : 0;
            if (aStreak != bStreak) {
                return Integer.compare(bStreak, aStreak);
            }
            if (a.hasMood() && !b.hasMood()) return -1;
            if (!a.hasMood() && b.hasMood()) return 1;
            if (a.hasMood() && b.hasMood() && a.mood().updatedAt() != null && b.mood().updatedAt() != null) {
                return b.mood().updatedAt().compareTo(a.mood().updatedAt());
            }
            return a.user().displayName().compareToIgnoreCase(b.user().displayName());
        });

        model.addAttribute("friendVibes", friendVibes);

        // Cùng con số với MediaLimits/Post để trình duyệt kiểm tra trước khi tải lên
        model.addAttribute("mediaLimits", Map.of(
                "maxImages", MediaLimits.MAX_IMAGES,
                "maxImageMb", MediaLimits.MAX_IMAGE_BYTES / (1024 * 1024),
                "maxVideoMb", MediaLimits.MAX_VIDEO_BYTES / (1024 * 1024),
                "maxChars", Post.MAX_CONTENT_LENGTH));
        return "home/feed";
    }
}
