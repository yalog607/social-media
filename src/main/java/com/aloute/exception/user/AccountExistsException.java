package com.aloute.exception.user;

/** Email hoặc username đã có người dùng. {@code field} là tên trường trên form để gắn lỗi. */
public class AccountExistsException extends RuntimeException {

    private final String field;

    public AccountExistsException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
