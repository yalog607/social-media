package com.aloute.chat;

/**
 * Chuỗi nhắn tin hằng ngày của một cuộc trò chuyện 1-1: số ngày liên tiếp mà CẢ HAI cùng nhắn ít nhất một tin.
 * {@code doneToday} false nghĩa là hôm nay chưa đủ hai bên — chuỗi vẫn giữ nhưng sẽ mất nếu hết ngày mà chưa nhắn.
 */
public record Streak(int days, boolean doneToday) {

    public static final Streak NONE = new Streak(0, false);

    public boolean active() {
        return days > 0;
    }

    /** Còn giữ được nhưng hôm nay chưa đủ hai bên nhắn. */
    public boolean atRisk() {
        return days > 0 && !doneToday;
    }
}
