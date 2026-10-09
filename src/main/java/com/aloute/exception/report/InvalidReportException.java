package com.aloute.exception.report;

/** Báo cáo không hợp lệ (báo cáo chính mình, đã báo cáo rồi, chi tiết quá dài...). Thông báo tiếng Việt, an toàn để hiển thị. */
public class InvalidReportException extends RuntimeException {
    public InvalidReportException(String message) {
        super(message);
    }
}
