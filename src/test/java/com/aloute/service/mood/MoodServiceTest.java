package com.aloute.service.mood;

import com.aloute.dto.mood.MoodForm;
import com.aloute.dto.mood.MoodView;
import com.aloute.dto.post.PostView;
import com.aloute.model.mood.DailyMood;
import com.aloute.model.user.Profile;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.mood.DailyMoodRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.social.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoodServiceTest {

    @Mock
    private DailyMoodRepository moods;

    @Mock
    private UserRepository users;

    @Mock
    private FriendService friends;

    private Clock clock;
    private MoodService moodService;

    private final Instant now = Instant.parse("2026-10-23T10:00:00Z");
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        moodService = new MoodService(moods, users, friends, clock);

        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        Profile profile = new Profile();
        profile.setDisplayName("Test User");
        profile.setAvatarUrl("/avatar.jpg");
        user.setProfile(profile);
    }

    @Test
    void setMood_whenNewUser_createsNewDailyMoodExpiringAtNextMidnight() {
        MoodForm form = new MoodForm("🌸", "Đang yêu đời", "pink");
        when(moods.findByUserId(userId)).thenReturn(Optional.empty());
        when(users.getReferenceById(userId)).thenReturn(user);
        when(moods.save(any(DailyMood.class))).thenAnswer(invocation -> {
            DailyMood m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        MoodView result = moodService.setMood(userId, form);

        assertThat(result).isNotNull();
        assertThat(result.emoji()).isEqualTo("🌸");
        assertThat(result.moodText()).isEqualTo("Đang yêu đời");
        assertThat(result.colorTheme()).isEqualTo("pink");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-10-24T00:00:00Z"));
        verify(moods).save(any(DailyMood.class));
    }

    @Test
    void setMood_whenExistingUser_updatesMoodAndExpiresAtNextMidnight() {
        DailyMood existing = new DailyMood();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setEmoji("⚡");
        existing.setMoodText("Chạy deadline");
        existing.setColorTheme("lemon");
        existing.setExpiresAt(now.plus(2, ChronoUnit.HOURS));

        when(moods.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(moods.save(existing)).thenReturn(existing);

        MoodForm form = new MoodForm("🧋", "Cần trà sữa gấp", "mint");
        MoodView result = moodService.setMood(userId, form);

        assertThat(result.emoji()).isEqualTo("🧋");
        assertThat(result.moodText()).isEqualTo("Cần trà sữa gấp");
        assertThat(result.colorTheme()).isEqualTo("mint");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-10-24T00:00:00Z"));
        verify(moods).save(existing);
    }

    @Test
    void setMood_whenBlankContent_throwsIllegalArgumentException() {
        MoodForm form = new MoodForm("🌸", "   ", "pink");

        assertThatThrownBy(() -> moodService.setMood(userId, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nội dung tâm trạng không được để trống");
    }

    @Test
    void setMood_whenContentTooLong_throwsIllegalArgumentException() {
        MoodForm form = new MoodForm("🌸", "a".repeat(61), "pink");

        assertThatThrownBy(() -> moodService.setMood(userId, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quá 60 ký tự");
    }

    @Test
    void getMyMood_whenActive_returnsMoodView() {
        DailyMood mood = new DailyMood();
        mood.setId(UUID.randomUUID());
        mood.setUser(user);
        mood.setEmoji("🌸");
        mood.setMoodText("Đang yêu đời");
        mood.setColorTheme("pink");
        mood.setExpiresAt(now.plus(12, ChronoUnit.HOURS));

        when(moods.findActiveByUserId(userId, now)).thenReturn(Optional.of(mood));

        Optional<MoodView> result = moodService.getMyMood(userId);

        assertThat(result).isPresent();
        assertThat(result.get().moodText()).isEqualTo("Đang yêu đời");
        assertThat(result.get().hoursRemaining(now)).isEqualTo(12);
    }

    @Test
    void getMyMood_whenNullUserId_returnsEmpty() {
        Optional<MoodView> result = moodService.getMyMood(null);
        assertThat(result).isEmpty();
        verifyNoInteractions(moods);
    }

    @Test
    void clearMood_deletesMood() {
        DailyMood mood = new DailyMood();
        mood.setId(UUID.randomUUID());
        mood.setUser(user);
        mood.setExpiresAt(now.plus(10, ChronoUnit.HOURS));

        when(moods.findByUserId(userId)).thenReturn(Optional.of(mood));

        moodService.clearMood(userId);

        verify(moods).delete(mood);
    }

    @Test
    void getFriendsMoods_returnsOnlyActiveMoodsOfFriends() {
        UUID friend1Id = UUID.randomUUID();
        UUID friend2Id = UUID.randomUUID();

        PostView.AuthorView f1 = new PostView.AuthorView(friend1Id, "f1", "Friend 1", null, Role.USER);
        PostView.AuthorView f2 = new PostView.AuthorView(friend2Id, "f2", "Friend 2", null, Role.USER);

        when(friends.friendsOf(userId)).thenReturn(List.of(f1, f2));

        User friendUser1 = new User();
        friendUser1.setId(friend1Id);
        friendUser1.setUsername("f1");
        Profile p1 = new Profile();
        p1.setDisplayName("Friend 1");
        friendUser1.setProfile(p1);

        DailyMood m1 = new DailyMood();
        m1.setId(UUID.randomUUID());
        m1.setUser(friendUser1);
        m1.setEmoji("☕");
        m1.setMoodText("Tập trung cao độ");
        m1.setColorTheme("sky");
        m1.setExpiresAt(now.plus(8, ChronoUnit.HOURS));

        when(moods.findActiveByUserIds(List.of(friend1Id, friend2Id), now)).thenReturn(List.of(m1));

        List<MoodView> result = moodService.getFriendsMoods(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().emoji()).isEqualTo("☕");
        assertThat(result.getFirst().user().username()).isEqualTo("f1");
    }
}
