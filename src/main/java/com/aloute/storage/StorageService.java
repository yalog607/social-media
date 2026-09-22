package com.aloute.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Lưu file người dùng tải lên và trả về URL công khai để hiển thị. */
public interface StorageService {

    /**
     * Lưu một ảnh (đã kiểm tra là ảnh thật) và trả về URL.
     *
     * @param folder thư mục logic, ví dụ "avatars", "covers"
     * @throws InvalidUploadException file không phải ảnh hợp lệ hoặc quá lớn
     */
    String storeImage(MultipartFile file, String folder, UUID ownerId);

    class InvalidUploadException extends RuntimeException {
        public InvalidUploadException(String message) {
            super(message);
        }
    }
}
