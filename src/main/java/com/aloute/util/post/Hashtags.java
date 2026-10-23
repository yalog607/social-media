package com.aloute.util.post;

import com.aloute.util.common.TextNormalizer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tách hashtag khỏi nội dung bài. Thẻ được lưu dạng chuẩn hóa (không dấu, chữ thường). */
public final class Hashtags {

    public static final int MAX_PER_POST = 10;
    public static final int MAX_TAG_LENGTH = 50;

    /**
     * {@code #} không được dính vào chữ/số/gạch dưới/{@code &} đứng trước (loại "abc#def" và thực thể HTML như {@code &#39;}),
     * và thẻ không được dài quá giới hạn (thẻ dài hơn bị bỏ hẳn, không cắt cụt).
     */
    static final Pattern PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}_&])#([\\p{L}\\p{N}_]{1," + MAX_TAG_LENGTH + "})(?![\\p{L}\\p{N}_])");

    private Hashtags() {
    }

    /** Các thẻ chuẩn hóa, không trùng, theo thứ tự xuất hiện, tối đa {@link #MAX_PER_POST}. */
    public static List<String> extract(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = PATTERN.matcher(text);
        while (matcher.find() && tags.size() < MAX_PER_POST) {
            String normalized = TextNormalizer.forSearch(matcher.group(1));
            if (!normalized.isEmpty()) {
                tags.add(normalized);
            }
        }
        return List.copyOf(tags);
    }
}
