package com.aloute.service.mood;

import com.aloute.dto.mood.MoodForm;
import com.aloute.dto.mood.MoodPreset;
import com.aloute.dto.mood.MoodView;
import com.aloute.dto.post.PostView;
import com.aloute.dto.social.AuthorViews;
import com.aloute.model.mood.DailyMood;
import com.aloute.model.user.User;
import com.aloute.repository.mood.DailyMoodRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.social.FriendService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MoodService {

    public static final List<MoodPreset> PRESETS = List.of(
            new MoodPreset("🥰", "Đang yêu đời", "pink"),
            new MoodPreset("🥳", "Hào hứng, vui vẻ", "lemon"),
            new MoodPreset("😴", "Cạn năng lượng", "lavender"),
            new MoodPreset("🫠", "Tan chảy, bất lực", "lemon"),
            new MoodPreset("😭", "Buồn muốn khóc", "sky"),
            new MoodPreset("😤", "Đang quạo, bực mình", "pink"),
            new MoodPreset("🥺", "Cần được dỗ dành", "mint"),
            new MoodPreset("🤯", "Quá tải, nổ tung đầu", "lavender"),
            new MoodPreset("😎", "Tự tin, đang chill", "mint"),
            new MoodPreset("🤔", "Đang suy tư", "sky"));

    private static final Set<String> ALLOWED_COLORS = Set.of("lemon", "pink", "mint", "lavender", "sky", "peach",
            "rose", "coral", "sunshine");

    private final DailyMoodRepository moods;
    private final UserRepository users;
    private final FriendService friends;
    private final Clock clock;

    public MoodService(DailyMoodRepository moods, UserRepository users, FriendService friends, Clock clock) {
        this.moods = moods;
        this.users = users;
        this.friends = friends;
        this.clock = clock;
    }

    public List<MoodPreset> getPresets() {
        return PRESETS;
    }

    @Transactional(readOnly = true)
    public Optional<MoodView> getMyMood(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return moods.findActiveByUserId(userId, clock.instant()).map(this::toView);
    }

    @Transactional(readOnly = true)
    public Optional<MoodView> getUserMood(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return moods.findActiveByUserId(userId, clock.instant()).map(this::toView);
    }

    @Transactional(readOnly = true)
    public List<MoodView> getFriendsMoods(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<PostView.AuthorView> friendList = friends.friendsOf(userId);
        if (friendList.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> friendIds = friendList.stream().map(PostView.AuthorView::id).toList();
        return moods.findActiveByUserIds(friendIds, clock.instant()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public MoodView setMood(UUID userId, MoodForm form) {
        if (userId == null) {
            throw new IllegalArgumentException("Người dùng chưa đăng nhập.");
        }
        String content = form.getMoodText() == null ? "" : form.getMoodText().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("Nội dung tâm trạng không được để trống.");
        }
        if (content.length() > 60) {
            throw new IllegalArgumentException("Nội dung tâm trạng không được quá 60 ký tự.");
        }

        String emoji = form.getEmoji() == null || form.getEmoji().isBlank() ? "🥰" : form.getEmoji().trim();
        String color = form.getColorTheme() != null && ALLOWED_COLORS.contains(form.getColorTheme())
                ? form.getColorTheme()
                : "lemon";

        Instant now = clock.instant();
        ZoneId zone = clock.getZone() != null ? clock.getZone() : ZoneId.systemDefault();
        Instant expiresAt = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();

        DailyMood mood = moods.findByUserId(userId).orElseGet(() -> {
            DailyMood newMood = new DailyMood();
            User userRef = users.getReferenceById(userId);
            newMood.setUser(userRef);
            return newMood;
        });

        mood.setEmoji(emoji);
        mood.setMoodText(content);
        mood.setColorTheme(color);
        mood.setExpiresAt(expiresAt);

        DailyMood saved = moods.save(mood);
        return toView(saved);
    }

    @Transactional
    public void clearMood(UUID userId) {
        if (userId == null) {
            return;
        }
        moods.findByUserId(userId).ifPresent(moods::delete);
    }

    private MoodView toView(DailyMood m) {
        return new MoodView(
                m.getId(),
                AuthorViews.of(m.getUser()),
                m.getEmoji(),
                m.getMoodText(),
                m.getColorTheme(),
                m.getUpdatedAt(),
                m.getExpiresAt());
    }
}
