package com.aloute.post;

import com.aloute.common.TextNormalizer;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

/**
 * Nội dung bài (văn bản thuần) → HTML an toàn để dùng với {@code th:utext}.
 * Luôn escape TRƯỚC, sau đó chỉ chèn những thẻ do server dựng (link hashtag, xuống dòng);
 * phần tên thẻ chỉ gồm chữ/số/gạch dưới nên không thể phá cấu trúc HTML.
 */
public final class PostTextRenderer {

    private PostTextRenderer() {
    }

    public static String toSafeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String escaped = HtmlUtils.htmlEscape(text, "UTF-8");

        Matcher matcher = Hashtags.PATTERN.matcher(escaped);
        StringBuilder out = new StringBuilder(escaped.length() + 32);
        while (matcher.find()) {
            String shown = matcher.group(1);
            String target = URLEncoder.encode(TextNormalizer.forSearch(shown), StandardCharsets.UTF_8);
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    "<a class=\"hashtag\" href=\"/tags/" + target + "\">#" + shown + "</a>"));
        }
        matcher.appendTail(out);

        return out.toString().replaceAll("\\r\\n|\\r|\\n", "<br>");
    }
}
