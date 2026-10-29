package com.aloute.dto.mood;

import com.aloute.dto.chat.Streak;
import com.aloute.dto.post.PostView.AuthorView;

/** DTO biểu diễn một người bạn trong thanh bạn bè (kết hợp tâm trạng vibe và chuỗi nhắn tin chatStreak). */
public record FriendVibeView(
        AuthorView user,
        MoodView mood,
        Streak chatStreak
) {
    public boolean hasMood() {
        return mood != null;
    }

    public boolean hasStreak() {
        return chatStreak != null && chatStreak.active();
    }

    public boolean isStreakAtRisk() {
        return hasStreak() && chatStreak.atRisk();
    }

    public int streakDays() {
        return hasStreak() ? chatStreak.days() : 0;
    }

    public String streakCrownClass() {
        if (!hasStreak()) {
            return "mood-streak-crown--zero";
        }
        return isStreakAtRisk() ? "mood-streak-crown--risk" : "mood-streak-crown--fire";
    }

    public String streakBadgeClass() {
        return streakCrownClass();
    }

    public String streakTitle() {
        String name = user != null ? user.displayName() : "bạn bè";
        if (hasStreak()) {
            if (isStreakAtRisk()) {
                return "Chuỗi nhắn tin 🔥 " + streakDays() + " ngày! Nhắn hôm nay để giữ chuỗi cùng " + name;
            }
            return "Chuỗi nhắn tin 🔥 " + streakDays() + " ngày cùng " + name;
        }
        return "Chưa có chuỗi · Nhắn tin cùng " + name + " để bắt đầu chuỗi 🔥!";
    }

    public String streakDisplayTitle() {
        return streakTitle();
    }

    public String itemTitle(java.time.Instant now) {
        StringBuilder sb = new StringBuilder(user != null ? user.displayName() : "");
        if (hasMood() && mood != null) {
            sb.append(": ").append(mood.emoji()).append(" ").append(mood.moodText())
              .append(" (còn ").append(mood.hoursRemaining(now)).append("h)");
        }
        if (hasStreak() && chatStreak != null) {
            sb.append(" · Chuỗi nhắn tin 🔥 ").append(chatStreak.days()).append(" ngày");
            if (chatStreak.atRisk()) {
                sb.append(" [Nhắn hôm nay để giữ chuỗi]");
            }
        }
        return sb.toString();
    }
}
