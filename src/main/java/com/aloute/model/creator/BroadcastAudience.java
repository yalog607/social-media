package com.aloute.model.creator;

/** Ai nhận tin nhắn hàng loạt: mọi người theo dõi. */
public enum BroadcastAudience {
    FOLLOWERS("Người theo dõi");

    private final String label;

    BroadcastAudience(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
