package com.aloute.util.post;

import com.aloute.util.common.TextNormalizer;
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
        return toSafeHtml(text, null);
    }

    public static String toSafeHtml(String text, java.util.function.Function<String, String> mentionResolver) {
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

        // Nhắc tên: @username -> liên kết tới trang cá nhân (username chỉ gồm chữ/số/_/. nên an toàn để đưa vào href)
        Matcher mention = Mentions.PATTERN.matcher(out);
        StringBuilder withMentions = new StringBuilder(out.length() + 32);
        while (mention.find()) {
            String name = Mentions.trimTrailingDots(mention.group(1));
            String dots = mention.group(1).substring(name.length());
            if (name.length() < 3) {
                mention.appendReplacement(withMentions, Matcher.quoteReplacement(mention.group()));
                continue;
            }
            String displayMention = "@" + name;
            if (mentionResolver != null) {
                String resolved = mentionResolver.apply(name.toLowerCase(java.util.Locale.ROOT));
                if (resolved != null) {
                    displayMention = resolved;
                }
            }
            mention.appendReplacement(withMentions, Matcher.quoteReplacement(
                    "<a class=\"mention\" href=\"/u/" + name.toLowerCase(java.util.Locale.ROOT) + "\">" + displayMention + "</a>" + dots));
        }
        mention.appendTail(withMentions);

        // Link nhận diện: https?://...
        Matcher url = java.util.regex.Pattern.compile("https?://(?:[^\\s&]|&amp;)+").matcher(withMentions);
        StringBuilder withUrls = new StringBuilder(withMentions.length() + 32);
        while (url.find()) {
            String href = url.group();
            String linkHtml = "<a class=\"text-link\" href=\"" + href + "\" target=\"_blank\" rel=\"noopener\">" + href + "</a>";
            url.appendReplacement(withUrls, Matcher.quoteReplacement(linkHtml));
        }
        url.appendTail(withUrls);

        // Huy hiệu Khoảnh khắc trong tin nhắn chat: [Khoảnh khắc](/stories/UUID) hoặc [Khoảnh khắc 24H]
        java.util.regex.Matcher storyLinkMatcher = java.util.regex.Pattern.compile("\\[Khoảnh khắc(?: 24H)?\\](?:\\(/stories/([a-fA-F0-9\\-]+)\\))?").matcher(withUrls);
        StringBuilder withStories = new StringBuilder(withUrls.length() + 32);
        while (storyLinkMatcher.find()) {
            String storyId = storyLinkMatcher.group(1);
            String href = storyId != null ? "/stories/" + storyId : "/";
            String dataAttr = storyId != null ? " data-story-id=\"" + storyId + "\"" : "";
            String badgeHtml = "<a href=\"" + href + "\" class=\"story-chat-badge text-decoration-none\"" + dataAttr + " title=\"Nhấn để xem khoảnh khắc\">"
                    + "<span class=\"story-chat-badge-pill\">📸 Khoảnh khắc ↗</span></a>";
            storyLinkMatcher.appendReplacement(withStories, Matcher.quoteReplacement(badgeHtml));
        }
        storyLinkMatcher.appendTail(withStories);

        // Trích dẫn trả lời tin nhắn: [reply:@Tên: "Nội dung"] hoặc [Trả lời @Tên: "Nội dung"]
        java.util.regex.Matcher replyMatcher = java.util.regex.Pattern.compile("\\[(?:reply|Trả lời) @([^:\\]]+): (?:&quot;|\")(.*?)(?:&quot;|\")\\]").matcher(withStories);
        StringBuilder withReplies = new StringBuilder(withStories.length() + 64);
        while (replyMatcher.find()) {
            String sender = replyMatcher.group(1);
            String snippet = replyMatcher.group(2);
            String quoteHtml = "<div class=\"msg-reply-quote\"><div class=\"msg-reply-quote-head\">"
                    + "<svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"vertical-align:-1px;margin-right:3px;\"><path d=\"M10 9V5l-7 7 7 7v-4.1c5 0 8.5 1.6 11 5.1-1-5-4-10-11-11z\"/></svg>"
                    + "<strong>@" + sender + "</strong></div><div class=\"msg-reply-quote-body\">"
                    + snippet + "</div></div>";
            replyMatcher.appendReplacement(withReplies, Matcher.quoteReplacement(quoteHtml));
        }
        replyMatcher.appendTail(withReplies);

        return withReplies.toString().replaceAll("\\r\\n|\\r|\\n", "<br>");
    }
}
