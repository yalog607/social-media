package com.aloute.model.chat;

/** Vai trò trong nhóm chat. Chủ nhóm có mọi quyền; quản trị viên sửa nhóm và quản lý thành viên thường; thành viên chỉ chat. */
public enum GroupRole {
    OWNER("Chủ nhóm", 3),
    ADMIN("Quản trị viên", 2),
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

    /** Được đổi tên/ảnh nhóm và thêm/xóa thành viên thường. */
    public boolean canManage() {
        return this != MEMBER;
    }

    /** Có quyền cao hơn {@code other} (chủ nhóm > quản trị viên > thành viên). */
    public boolean outranks(GroupRole other) {
        return rank > other.rank;
    }
}
