package com.aloute.wallet;

import java.util.Optional;

/** Huy hiệu fan theo tổng số Xu đã tặng một Creator. Donate chỉ tăng nên huy hiệu không bao giờ bị hạ. */
public enum FanBadge {
    BRONZE("Fan đồng", "🥉", 10),
    SILVER("Fan bạc", "🥈", 50),
    GOLD("Fan vàng", "🥇", 200);

    private final String label;
    private final String emoji;
    private final long threshold;

    FanBadge(String label, String emoji, long threshold) {
        this.label = label;
        this.emoji = emoji;
        this.threshold = threshold;
    }

    public String label() {
        return label;
    }

    public String emoji() {
        return emoji;
    }

    public long threshold() {
        return threshold;
    }

    /** Huy hiệu cao nhất mà {@code totalDonated} đạt được; rỗng nếu chưa tới ngưỡng thấp nhất. */
    public static Optional<FanBadge> forTotal(long totalDonated) {
        FanBadge best = null;
        for (FanBadge badge : values()) {
            if (totalDonated >= badge.threshold) {
                best = badge;
            }
        }
        return Optional.ofNullable(best);
    }
}
