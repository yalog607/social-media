package com.aloute.util.storage;

import com.aloute.service.storage.StorageService;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Nhận diện ảnh bằng chữ ký byte (magic bytes) thay vì tin vào Content-Type/tên file từ client.
 * Cố ý không hỗ trợ SVG vì SVG có thể chứa script.
 */
public final class ImageSniffer {

    /** Giới hạn dung lượng của ảnh đại diện/ảnh bìa (bài đăng có giới hạn riêng ở {@code MediaLimits}). */
    static final long MAX_BYTES = 5L * 1024 * 1024;

    public record Detected(String extension, String contentType) {
    }

    private ImageSniffer() {
    }

    /** Nhận diện định dạng theo nội dung, không kiểm tra dung lượng. Rỗng nếu không phải JPG/PNG/GIF/WEBP. */
    public static Optional<Detected> detect(byte[] data) {
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(new Detected("jpg", "image/jpeg"));
        }
        if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(new Detected("png", "image/png"));
        }
        if (startsWith(data, 'G', 'I', 'F', '8')) {
            return Optional.of(new Detected("gif", "image/gif"));
        }
        if (data.length > 12
                && new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return Optional.of(new Detected("webp", "image/webp"));
        }
        return Optional.empty();
    }

    /** @return kiểu ảnh nếu nhận ra, ngược lại ném {@link StorageService.InvalidUploadException} */
    public static Detected sniff(byte[] data) {
        if (data.length == 0) {
            throw new StorageService.InvalidUploadException("File trống");
        }
        if (data.length > MAX_BYTES) {
            throw new StorageService.InvalidUploadException("Ảnh quá lớn, tối đa 5 MB");
        }
        return detect(data).orElseThrow(
                () -> new StorageService.InvalidUploadException("Chỉ nhận ảnh JPG, PNG, GIF hoặc WEBP"));
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
