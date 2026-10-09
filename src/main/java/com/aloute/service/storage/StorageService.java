package com.aloute.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Lưu file người dùng tải lên và trả về URL công khai để hiển thị. */
public interface StorageService {

    /**
     * Lưu một ảnh (đã kiểm tra là ảnh thật) và trả về URL. Dùng cho ảnh đại diện/ảnh bìa.
     *
     * @param folder thư mục logic, ví dụ "avatars", "covers"
     * @throws InvalidUploadException file không phải ảnh hợp lệ hoặc quá lớn
     */
    String storeImage(MultipartFile file, String folder, UUID ownerId);

    /**
     * Lưu dữ liệu đã được kiểm tra/xử lý ở nơi khác. Tên file do server sinh, phần mở rộng do người gọi quyết định
     * (dựa trên nội dung thật, không phải tên file của client).
     *
     * @throws IllegalArgumentException {@code folder} hoặc {@code extension} có ký tự không cho phép
     */
    String storeBytes(byte[] data, String folder, String extension);

    /** Như {@link #storeBytes} nhưng ghi thẳng từ luồng xuống đĩa, dùng cho file lớn (video). */
    String storeStream(InputStream in, String folder, String extension) throws IOException;

    /**
     * Xóa một file đã lưu theo URL do {@code store*} trả về; không lỗi nếu file đã mất.
     *
     * @throws IllegalArgumentException URL không thuộc khu vực lưu trữ (chống xóa file ngoài thư mục)
     */
    void delete(String url);

    class InvalidUploadException extends RuntimeException {
        public InvalidUploadException(String message) {
            super(message);
        }
    }
}
