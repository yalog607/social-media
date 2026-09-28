package com.aloute.storage;

import com.aloute.config.AlouteProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lưu file vào thư mục trên đĩa máy chủ, phục vụ qua /uploads/**. Production gắn Docker volume vào thư mục này.
 * Mặc định ({@code aloute.storage.type} trống hoặc {@code local}); {@link CloudinaryStorageService} thay thế
 * khi đặt {@code aloute.storage.type=cloudinary}.
 */
@Service
@ConditionalOnProperty(prefix = "aloute.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final String URL_PREFIX = "/uploads/";
    private static final Pattern SAFE_FOLDER = Pattern.compile("[a-z0-9_-]{1,30}");
    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,5}");

    private final Path root;

    public LocalStorageService(AlouteProperties props) {
        this.root = Path.of(props.storage().localDir()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public String storeImage(MultipartFile file, String folder, UUID ownerId) {
        try {
            byte[] data = file.getBytes();
            ImageSniffer.Detected type = ImageSniffer.sniff(data);
            // Tên do server sinh; folder chỉ là hằng số nội bộ nên không lo path traversal từ client
            Path dir = directoryFor(folder);
            String name = ownerId + "-" + UUID.randomUUID() + "." + type.extension();
            Files.write(dir.resolve(name), data);
            return URL_PREFIX + folder + "/" + name;
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được file", e);
        }
    }

    @Override
    public String storeBytes(byte[] data, String folder, String extension) {
        try {
            Path dir = directoryFor(folder);
            String name = newName(extension);
            Files.write(dir.resolve(name), data);
            return URL_PREFIX + folder + "/" + name;
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được file", e);
        }
    }

    @Override
    public String storeStream(InputStream in, String folder, String extension) throws IOException {
        Path dir = directoryFor(folder);
        String name = newName(extension);
        try {
            Files.copy(in, dir.resolve(name));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(dir.resolve(name)); // không để lại file dở dang
            throw e;
        }
        return URL_PREFIX + folder + "/" + name;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            throw new IllegalArgumentException("URL không thuộc khu vực lưu trữ");
        }
        Path target = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("URL không thuộc khu vực lưu trữ");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Không xóa được file", e);
        }
    }

    private Path directoryFor(String folder) throws IOException {
        if (folder == null || !SAFE_FOLDER.matcher(folder).matches()) {
            throw new IllegalArgumentException("Thư mục lưu trữ không hợp lệ");
        }
        Path dir = root.resolve(folder).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("Thư mục lưu trữ không hợp lệ");
        }
        return Files.createDirectories(dir);
    }

    private static String newName(String extension) {
        if (extension == null || !SAFE_EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Phần mở rộng không hợp lệ");
        }
        return UUID.randomUUID() + "." + extension;
    }
}
