package com.aloute.storage;

import com.aloute.config.AlouteProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Lưu ảnh vào thư mục cục bộ (dev/test), phục vụ qua /uploads/**. */
@Service
@ConditionalOnProperty(name = "aloute.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

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
            String name = ownerId + "-" + UUID.randomUUID() + "." + type.extension();
            Path dir = root.resolve(folder).normalize();
            if (!dir.startsWith(root)) {
                throw new IllegalArgumentException("Thư mục không hợp lệ");
            }
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), data);
            return "/uploads/" + folder + "/" + name;
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được file", e);
        }
    }
}
