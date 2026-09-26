package com.aloute.support;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Dữ liệu ảnh/video hợp lệ cho test tích hợp. */
public final class TestMedia {

    /** Thư mục lưu file của profile test (xem application-test.yml). */
    public static final Path POSTS_DIR = Path.of("target/test-uploads/posts");

    private TestMedia() {
    }

    public static byte[] jpeg(int width, int height) {
        return encode(width, height, BufferedImage.TYPE_INT_RGB, "jpg");
    }

    public static byte[] png(int width, int height) {
        return encode(width, height, BufferedImage.TYPE_INT_ARGB, "png");
    }

    private static byte[] encode(int width, int height, int type, String format) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.PINK);
        g.fillRect(0, 0, width, height);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Byte đủ giống MP4 (hộp ftyp/isom) để qua bộ nhận diện; không phải video phát được. */
    public static byte[] mp4(int size) {
        byte[] data = new byte[size];
        byte[] head = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(head, 0, data, 0, head.length);
        return data;
    }

    public static MultipartFile image(String name, byte[] content) {
        return new MockMultipartFile("images", name, "image/jpeg", content);
    }

    public static MultipartFile video(String name, byte[] content) {
        return new MockMultipartFile("video", name, "video/mp4", content);
    }

    /** Số file hiện có trong thư mục media của bài đăng (để chứng minh không bị bỏ rác). */
    public static long storedFileCount() {
        if (!Files.exists(POSTS_DIR)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(POSTS_DIR)) {
            return files.count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
