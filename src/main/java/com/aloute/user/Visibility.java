package com.aloute.user;

/** Mức hiển thị dùng cho hồ sơ và bài viết mặc định. */
public enum Visibility {
    PUBLIC("Công khai"),
    FRIENDS("Chỉ bạn bè"),
    PRIVATE("Chỉ mình tôi");

    private final String label;

    Visibility(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
