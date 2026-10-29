package com.aloute.model.chat;

/**
 * Vai trò trong hội nhóm/nhóm chat.
 * OWNER: Chủ nhóm (toàn quyền)
 * ADMIN: Quản trị viên (quản lý thành viên, kiểm duyệt, cài đặt)
 * MODERATOR: Kiểm duyệt viên (duyệt thành viên, ghim/xóa bài vi phạm, xử lý báo cáo)
 * MEMBER: Thành viên thông thường
 */
public enum GroupRole {
    OWNER("Chủ nhóm", 4),
    ADMIN("Quản trị viên", 3),
    MODERATOR("Kiểm duyệt viên", 2),
    MEMBER("Thành viên", 1);

    private final String label;
    private final int rank;

    GroupRole(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public int rank() {
        return rank;
    }

    /** Được đổi tên/ảnh nhóm, cài đặt và quản lý thành viên. */
    public boolean canManage() {
        return this == OWNER || this == ADMIN;
    }

    /** Được kiểm duyệt nội dung, duyệt thành viên, ghim bài. */
    public boolean canModerate() {
        return this != MEMBER;
    }

    /** Có quyền cao hơn hoặc bằng {@code minRole}. */
    public boolean atLeast(GroupRole minRole) {
        return this.rank >= minRole.rank;
    }

    /** Có quyền cao hơn {@code other}. */
    public boolean outranks(GroupRole other) {
        return this.rank > other.rank;
    }
}
