package com.aloute.model.moderation;

/** Cách Manager xử lý một báo cáo. */
public enum ReportAction {
    DISMISS("Bác bỏ báo cáo"),
    REMOVE_CONTENT("Xóa nội dung"),
    WARN("Cảnh cáo người đăng"),
    SUSPEND("Khóa tài khoản người đăng");

    private final String label;

    ReportAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
