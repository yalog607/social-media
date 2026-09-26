package com.aloute.common;

import java.time.Duration;

/** Các hành động bị giới hạn tần suất theo từng người dùng, kèm ngưỡng và thông báo tiếng Việt. */
public enum RateAction {
    POST(10, Duration.ofMinutes(10), "Bạn đăng bài hơi nhanh rồi, đợi ít phút rồi thử lại nhé."),
    COMMENT(30, Duration.ofMinutes(5), "Bạn bình luận hơi nhanh, đợi một chút rồi thử lại nhé."),
    REACTION(120, Duration.ofMinutes(1), "Bạn thả cảm xúc hơi nhanh, đợi một chút rồi thử lại nhé.");

    private final int max;
    private final Duration window;
    private final String message;

    RateAction(int max, Duration window, String message) {
        this.max = max;
        this.window = window;
        this.message = message;
    }

    /** Số lần tối đa trong một cửa sổ. */
    public int max() {
        return max;
    }

    public Duration window() {
        return window;
    }

    public String message() {
        return message;
    }
}
