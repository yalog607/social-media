package com.aloute.dto.mood;

import com.aloute.dto.chat.Streak;
import com.aloute.dto.post.PostView.AuthorView;

/** DTO biểu diễn một người bạn trong thanh story bạn bè (kết hợp tâm trạng vibe và chuỗi nhắn tin chatStreak). */
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
        return isStreakAtRisk() ? "mood-streak-crown--risk" : "mood-streak-crown--fire";
    }

    public String streakTitle() {
        if (!hasStreak() || chatStreak == null) return "";
        String name = user != null ? user.displayName() : "bạn bè";
        if (chatStreak.atRisk()) {
            return "Chuỗi nhắn tin 🔥 " + chatStreak.days() + " ngày! Nhắn hôm nay để giữ chuỗi cùng " + name;
        }
        return "Chuỗi nhắn tin 🔥 " + chatStreak.days() + " ngày cùng " + name;
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
