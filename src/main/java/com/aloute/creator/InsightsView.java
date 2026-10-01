package com.aloute.creator;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Số liệu tương tác của một Creator trong {@code days} ngày gần nhất (tính theo ngày UTC). Không có "lượt xem":
 * hệ thống chưa ghi nhận lượt xem, chỉ có cảm xúc, bình luận, chia sẻ và người theo dõi mới.
 */
public record InsightsView(
        int days,
        long followers,
        long newFollowers,
        long reactions,
        long comments,
        long shares,
        List<DayPoint> series,
        List<TopPost> topPosts) {

    public record DayPoint(LocalDate date, long reactions, long comments, long shares, long followers) {

        public long engagement() {
            return reactions + comments + shares;
        }
    }

    /** {@code snippet} là chữ thuần (đã cắt ngắn), không phải HTML. */
    public record TopPost(UUID id, String snippet, long reactions, long comments, long shares) {

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
