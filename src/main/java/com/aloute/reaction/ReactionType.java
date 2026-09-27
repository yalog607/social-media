package com.aloute.reaction;

/** 5 loại cảm xúc cho một bài viết. Mỗi người chỉ giữ một loại cho mỗi bài (xem {@link ReactionService}). */
public enum ReactionType {
    LOVE("Yêu thích", "❤️"),
    HAHA("Haha", "😂"),
    FIRE("Cháy quá", "🔥"),
    WOW("Wow", "😮"),
    SAD("Buồn ghê", "😢");

    private final String label;
    private final String emoji;

    ReactionType(String label, String emoji) {
        this.label = label;
        this.emoji = emoji;
    }

    public String label() {
        return label;
    }

    public String emoji() {
        return emoji;
    }
}
