package com.aloute.storage;

import com.aloute.config.AlouteProperties;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lưu file trên Cloudinary thay vì đĩa máy chủ. Bật bằng {@code aloute.storage.type=cloudinary} (biến môi trường
 * {@code ALOUTE_STORAGE_TYPE}) kèm {@code CLOUDINARY_CLOUD_NAME}/{@code CLOUDINARY_API_KEY}/{@code CLOUDINARY_API_SECRET}.
 * Mặc định vẫn dùng {@link LocalStorageService} nên không cần tài khoản Cloudinary khi phát triển/kiểm thử.
 */
@Service
@ConditionalOnProperty(prefix = "aloute.storage", name = "type", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryStorageService.class);

    private static final Pattern SAFE_FOLDER = Pattern.compile("[a-z0-9_-]{1,30}");
    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,5}");

    /** Cloudinary nhúng resource_type (image/video/raw) và public_id (không kèm phần mở rộng) ngay trong URL. */
    private static final Pattern SECURE_URL = Pattern.compile(
            "^https://res\\.cloudinary\\.com/([^/]+)/(image|video|raw)/upload/v\\d+/(.+?)(?:\\.[a-zA-Z0-9]+)?$");

    private final Cloudinary cloudinary;
    private final String cloudName;

    public CloudinaryStorageService(AlouteProperties props) {
        AlouteProperties.Storage.Cloudinary cfg = props.storage().cloudinary();
        if (cfg == null || !cfg.configured()) {
            throw new IllegalStateException(
                    "aloute.storage.type=cloudinary nhưng thiếu CLOUDINARY_CLOUD_NAME/CLOUDINARY_API_KEY/CLOUDINARY_API_SECRET");
        }
        this.cloudName = cfg.cloudName();
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cfg.cloudName(),
                "api_key", cfg.apiKey(),
                "api_secret", cfg.apiSecret(),
                "secure", true));
    }

    @Override
    public String storeImage(MultipartFile file, String folder, UUID ownerId) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file", e);
        }
        ImageSniffer.sniff(data); // chỉ để kiểm tra định dạng thật, ném InvalidUploadException nếu không phải ảnh
        return upload(data, folder, ownerId + "-" + UUID.randomUUID(), "image");
    }

    @Override
    public String storeBytes(byte[] data, String folder, String extension) {
        validateFolder(folder);
        validateExtension(extension);
        return upload(data, folder, UUID.randomUUID().toString(), "auto");
    }

    @Override
    public String storeStream(InputStream in, String folder, String extension) throws IOException {
        // MediaLimits đã giới hạn video ở 25 MB nên gộp hết vào bộ nhớ trước khi gửi lên là an toàn.
        return storeBytes(in.readAllBytes(), folder, extension);
    }

    @Override
    public void delete(String url) {
        Matcher matcher = matchOwnCloud(url);
        if (matcher == null) {
            throw new IllegalArgumentException("URL không thuộc khu vực lưu trữ");
        }
        try {
            cloudinary.uploader().destroy(matcher.group(3), ObjectUtils.asMap("resource_type", matcher.group(2)));
        } catch (IOException e) {
            log.warn("Không xóa được file Cloudinary {}: {}", url, e.getMessage());
        }
    }

    private String upload(byte[] data, String folder, String publicId, String resourceType) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(data, ObjectUtils.asMap(
                    "folder", folder,
                    "public_id", publicId,
                    "resource_type", resourceType,
                    "overwrite", true));
            return String.valueOf(result.get("secure_url"));
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được file lên Cloudinary", e);
        }
    }

    private Matcher matchOwnCloud(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = SECURE_URL.matcher(url);
        return matcher.matches() && cloudName.equals(matcher.group(1)) ? matcher : null;
    }

    private static void validateFolder(String folder) {
        if (folder == null || !SAFE_FOLDER.matcher(folder).matches()) {
            throw new IllegalArgumentException("Thư mục lưu trữ không hợp lệ");
        }
    }

    private static void validateExtension(String extension) {
        if (extension == null || !SAFE_EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Phần mở rộng không hợp lệ");
        }
    }
}
