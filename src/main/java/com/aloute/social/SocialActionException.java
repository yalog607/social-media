package com.aloute.social;

/** Một thao tác kết bạn/theo dõi/chặn không hợp lệ (tự thao tác với chính mình, đã bị chặn, sai trạng thái...). */
public class SocialActionException extends RuntimeException {
    public SocialActionException(String message) {
        super(message);
    }
}
