package com.aloute.util.media;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * Nhận diện video MP4/WEBM bằng chữ ký byte ở đầu file, không tin tên file hay Content-Type do client gửi.
 * Chỉ nhận brand MP4 chuẩn để không nhầm với ảnh HEIC (cũng là hộp {@code ftyp}); không nhận QuickTime/MKV.
 */
public final class VideoSniffer {

    public record VideoType(String extension, String contentType) {
    }

    /** Số byte đầu file cần để nhận diện. */
    public static final int HEAD_BYTES = 64;

    private static final Set<String> MP4_BRANDS =
            Set.of("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "avc1", "mp41", "mp42", "M4V ", "mmp4", "dash");

    private VideoSniffer() {
    }

    public static Optional<VideoType> sniff(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (head.length >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            String brand = new String(head, 8, 4, StandardCharsets.ISO_8859_1);
            return MP4_BRANDS.contains(brand)
                    ? Optional.of(new VideoType("mp4", "video/mp4"))
                    : Optional.empty();
        }
        if (head.length >= 8 && (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45
                && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3) {
            String start = new String(head, 0, Math.min(head.length, HEAD_BYTES), StandardCharsets.ISO_8859_1);
            return start.contains("webm")
                    ? Optional.of(new VideoType("webm", "video/webm"))
                    : Optional.empty();
        }
        return Optional.empty();
    }
}
