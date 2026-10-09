package com.aloute.util.post;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nhắc tên kiểu {@code @username} trong bài, bình luận và tin nhắn. Username gồm chữ thường, số, gạch dưới và dấu chấm
 * (3–24 ký tự); không tính khi dính liền vào chữ phía trước (như địa chỉ email) và bỏ dấu chấm cuối câu.
 */
public final class Mentions {

    public static final int MAX_PER_TEXT = 10;

    static final Pattern PATTERN = Pattern.compile("(?<![\\p{L}\\p{N}_.@&])@([A-Za-z0-9_.]{3,24})");

    private Mentions() {
    }

    /** Các username được nhắc (viết thường, không trùng, theo thứ tự xuất hiện, tối đa {@value #MAX_PER_TEXT}). */
    public static Set<String> extract(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null) {
            return names;
        }
        Matcher matcher = PATTERN.matcher(text);
        while (matcher.find() && names.size() < MAX_PER_TEXT) {
            String name = trimTrailingDots(matcher.group(1)).toLowerCase(Locale.ROOT);
            if (name.length() >= 3) {
                names.add(name);
            }
        }
        return names;
    }

    /** Phần username thật (không kèm dấu chấm cuối câu) của một khớp. */
    static String trimTrailingDots(String raw) {
        int end = raw.length();
        while (end > 0 && raw.charAt(end - 1) == '.') {
            end--;
        }
        return raw.substring(0, end);
    }
}
