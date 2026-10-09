package com.aloute.model.wallet;

/** Loại giao dịch trong sổ cái; {@code credit} cho biết dòng này cộng hay trừ vào số dư. */
public enum WalletTxType {
    SIGNUP_BONUS("Quà chào mừng", true),
    DAILY_BONUS("Xu hằng ngày", true),
    DONATE_SENT("Tặng Xu", false),
    DONATE_RECEIVED("Nhận donate", true),
    UNLOCK_SPENT("Mở khóa bài", false),
    UNLOCK_EARNED("Bán nội dung", true);

    private final String label;
    private final boolean credit;

    WalletTxType(String label, boolean credit) {
        this.label = label;
        this.credit = credit;
    }

    public String label() {
        return label;
    }

    public boolean credit() {
        return credit;
    }
}
