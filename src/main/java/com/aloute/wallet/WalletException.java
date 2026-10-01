package com.aloute.wallet;

/** Giao dịch Xu không hợp lệ (không đủ Xu, số Xu sai, tự donate...). Thông báo tiếng Việt, an toàn để hiển thị. */
public class WalletException extends RuntimeException {
    public WalletException(String message) {
        super(message);
    }
}
