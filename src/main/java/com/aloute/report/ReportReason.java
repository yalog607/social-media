package com.aloute.report;

/** Lý do báo cáo mà người dùng chọn. */
public enum ReportReason {
    SPAM("Spam hoặc quảng cáo"),
    HARASSMENT("Quấy rối, xúc phạm"),
    INAPPROPRIATE("Nội dung không phù hợp"),
    MISINFO("Thông tin sai lệch"),
    OTHER("Lý do khác");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
