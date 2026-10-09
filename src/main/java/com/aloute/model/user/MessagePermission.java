package com.aloute.model.user;

/** Ai được nhắn tin cho mình (áp dụng ở GĐ3). */
public enum MessagePermission {
    EVERYONE("Mọi người"),
    FRIENDS("Chỉ bạn bè"),
    NOBODY("Không ai");

    private final String label;

    MessagePermission(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
