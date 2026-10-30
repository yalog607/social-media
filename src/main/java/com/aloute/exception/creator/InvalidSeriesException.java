package com.aloute.exception.creator;

/** Series không hợp lệ (tiêu đề trống/quá dài, quá nhiều series/bài, bài không thêm được...). Thông báo tiếng Việt. */
public class InvalidSeriesException extends RuntimeException {
    public InvalidSeriesException(String message) {
        super(message);
    }
}
