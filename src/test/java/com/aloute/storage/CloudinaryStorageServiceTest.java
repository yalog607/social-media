package com.aloute.storage;

import com.aloute.config.AlouteProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Không gọi mạng thật (không có tài khoản Cloudinary trong CI): chỉ kiểm tra phần chạy được mà không cần gọi API
 * — từ chối cấu hình thiếu, validate/từ chối URL trước khi thử gọi Cloudinary, và việc {@code delete} định tuyến
 * đúng chỗ khi URL là của bản lưu tạm trên đĩa (xem {@link CloudinaryStorageService} — lưu tạm khi Cloudinary
 * từ chối yêu cầu, ví dụ hết hạn mức).
 */
class CloudinaryStorageServiceTest {

    @TempDir Path root;

    private AlouteProperties propsWith(AlouteProperties.Storage.Cloudinary cloudinary) {
        return new AlouteProperties(null, null, null, null, null,
                new AlouteProperties.Storage("cloudinary", root.toString(), cloudinary), null, null);
    }

    private CloudinaryStorageService service() {
        return new CloudinaryStorageService(
                propsWith(new AlouteProperties.Storage.Cloudinary("demo-cloud", "key123", "secret123")));
    }

    @Test
    void refusesToStartWithoutFullCredentials() {
        assertThatThrownBy(() -> new CloudinaryStorageService(propsWith(null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CloudinaryStorageService(
                propsWith(new AlouteProperties.Storage.Cloudinary("demo-cloud", "", "secret123"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsFoldersAndExtensionsThatLookUnsafeBeforeCallingCloudinary() {
        CloudinaryStorageService storage = service();

        assertThatThrownBy(() -> storage.storeBytes(new byte[]{1}, "../evil", "jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.storeBytes(new byte[]{1}, "posts", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRefusesUrlsThatAreNotFromOurOwnCloudinaryCloudOrOurLocalFallback() {
        CloudinaryStorageService storage = service();

        assertThatThrownBy(() -> storage.delete("https://evil.example/uploads/x.png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("https://res.cloudinary.com/someone-else/image/upload/v1/x.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRoutesLocalFallbackUrlsToTheLocalDisk() throws Exception {
        CloudinaryStorageService storage = service();
        // Giả lập một file đã được lưu tạm trên đĩa lúc trước (Cloudinary từng từ chối yêu cầu lúc lưu nó)
        LocalStorageService plainLocal = new LocalStorageService(propsWith(null));
        String url = plainLocal.storeBytes(new byte[]{1, 2, 3}, "posts", "jpg");
        Path file = root.resolve(url.substring("/uploads/".length()));
        assertThat(Files.exists(file)).isTrue();

        storage.delete(url);

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void storeImageRejectsFilesThatAreNotRealImages() {
        CloudinaryStorageService storage = service();
        var fake = new org.springframework.mock.web.MockMultipartFile("avatar", "evil.png", "image/png",
                "<svg><script>alert(1)</script></svg>".getBytes());

        assertThatThrownBy(() -> storage.storeImage(fake, "avatars", UUID.randomUUID()))
                .isInstanceOf(StorageService.InvalidUploadException.class);
    }
}
