package com.aloute.creator;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Số liệu tương tác của một Creator trong {@code days} ngày gần nhất (tính theo ngày UTC). Lượt xem chỉ tính người đã
 * đăng nhập (mỗi người một lượt mỗi bài mỗi ngày, không tính chính chủ bài).
 */
public record InsightsView(
        int days,
        long followers,
        long newFollowers,
        long reactions,
        long comments,
        long shares,
        long views,
        List<DayPoint> series,
        List<TopPost> topPosts) {

    public record DayPoint(LocalDate date, long reactions, long comments, long shares, long followers, long views) {

        public long engagement() {
            return reactions + comments + shares;
        }
    }

    /** {@code snippet} là chữ thuần (đã cắt ngắn), không phải HTML. */
    public record TopPost(UUID id, String snippet, long reactions, long comments, long shares, long views) {

        public long total() {
            return reactions + comments + shares;
        }
    }

    public long engagement() {
        return reactions + comments + shares;
    }

    public long maxDailyEngagement() {
        return series.stream().mapToLong(DayPoint::engagement).max().orElse(0);
    }

    public long maxDailyViews() {
        return series.stream().mapToLong(DayPoint::views).max().orElse(0);
    }

    public long maxDailyFollowers() {
        return series.stream().mapToLong(DayPoint::followers).max().orElse(0);
    }

    /** Chiều cao cột (0–100) của {@code value} so với {@code max}; có số liệu thì tối thiểu 4% để vẫn nhìn thấy. */
    public static int percent(long value, long max) {
        if (value <= 0 || max <= 0) {
            return 0;
        }
        return (int) Math.max(4, Math.round(100.0 * value / max));
    }
}
