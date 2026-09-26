package com.aloute.feed;

import com.aloute.post.Post;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Con trỏ phân trang: bản ghi CUỐI của trang trước, gồm {@code (created_at, id)}. Truy vấn lấy các bài "cũ hơn"
 * con trỏ nên bài mới chen vào giữa hai lần tải không làm lặp hay mất bài (khác với phân trang theo số trang).
 * Thời gian mã hóa ở độ chính xác micro giây vì đó là độ chính xác PostgreSQL lưu.
 */
public record Cursor(Instant createdAt, UUID id) {

    /** Con trỏ "sau tất cả": dùng cho trang đầu để câu truy vấn không phải xử lý tham số null. */
    public static final Cursor START =
            new Cursor(Instant.parse("9999-12-31T00:00:00Z"), UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));

    private static final int MAX_TOKEN_LENGTH = 200;

    public static Cursor of(Post post) {
        return new Cursor(post.getCreatedAt(), post.getId());
    }

    public String encode() {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, createdAt);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((micros + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** Giải mã token từ client. Bất kỳ sai lệch nào cũng cho kết quả rỗng (coi như trang đầu), không bao giờ ném lỗi. */
    public static Optional<Cursor> decode(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        try {
            String text = new String(Base64.getUrlDecoder().decode(token.trim()), StandardCharsets.UTF_8);
            int separator = text.indexOf('|');
            if (separator <= 0) {
                return Optional.empty();
            }
            long micros = Long.parseLong(text.substring(0, separator));
            if (micros < 0) {
                return Optional.empty();
            }
            UUID id = UUID.fromString(text.substring(separator + 1));
            return Optional.of(new Cursor(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id));
        } catch (RuntimeException e) {
            // Base64, số hoặc UUID không hợp lệ, hoặc số vượt phạm vi Instant
            return Optional.empty();
        }
    }
}
