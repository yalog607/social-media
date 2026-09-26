package com.aloute.common;

import java.text.Normalizer;
import java.util.Locale;

/** Chuẩn hóa văn bản để tìm kiếm: bỏ dấu tiếng Việt, {@code đ → d}, chữ thường, gộp khoảng trắng. */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String forSearch(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        // "đ" không có dạng phân rã nên phải thay riêng
        String noDiacritics = decomposed.replace('đ', 'd').replace('Đ', 'D');
        return noDiacritics.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
