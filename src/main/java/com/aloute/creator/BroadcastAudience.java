package com.aloute.creator;

/** Ai nhận tin nhắn hàng loạt: mọi người theo dõi, hay chỉ những fan đã đạt huy hiệu. */
public enum BroadcastAudience {
    FOLLOWERS("Người theo dõi"),
    FANS("Fan (đã tặng Xu)");

    private final String label;

    BroadcastAudience(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
