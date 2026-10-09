package com.aloute.storage;

import com.aloute.service.storage.LocalStorageService;

import com.aloute.config.AlouteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    @TempDir Path root;
    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(new AlouteProperties(null, null, null, null, null,
                new AlouteProperties.Storage("local", root.toString(), null), null, null));
    }

    private Path fileOf(String url) {
        return root.resolve(url.substring("/uploads/".length()));
    }

    @Test
    void storeBytesWritesAServerNamedFileAndReturnsItsPublicUrl() throws IOException {
        String url = storage.storeBytes(new byte[]{1, 2, 3}, "posts", "jpg");

        assertThat(url).matches("/uploads/posts/[0-9a-f-]{36}\\.jpg");
        assertThat(Files.readAllBytes(fileOf(url))).containsExactly(1, 2, 3);
    }

    @Test
    void storeStreamCopiesTheContentWithoutLoadingItAll() throws IOException {
        byte[] content = new byte[100_000];
        content[99_999] = 7;

        String url = storage.storeStream(new ByteArrayInputStream(content), "posts", "mp4");

        assertThat(url).endsWith(".mp4");
        assertThat(Files.readAllBytes(fileOf(url))).isEqualTo(content);
    }

    @Test
    void everyStoredFileGetsADifferentName() {
        String a = storage.storeBytes(new byte[]{1}, "posts", "png");
        String b = storage.storeBytes(new byte[]{1}, "posts", "png");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void rejectsFolderAndExtensionThatCouldEscapeTheUploadRoot() {
        assertThatThrownBy(() -> storage.storeBytes(new byte[]{1}, "../evil", "jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.storeBytes(new byte[]{1}, "posts/../../x", "jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.storeBytes(new byte[]{1}, "posts", "jpg/../../x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.storeBytes(new byte[]{1}, "posts", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesTheFileAndIsHarmlessWhenItIsAlreadyGone() {
        String url = storage.storeBytes(new byte[]{1}, "posts", "png");
        assertThat(Files.exists(fileOf(url))).isTrue();

        storage.delete(url);
        storage.delete(url); // lần hai: không có gì để xóa, không được lỗi

        assertThat(Files.exists(fileOf(url))).isFalse();
    }

    @Test
    void deleteRefusesUrlsOutsideTheUploadArea() throws IOException {
        Path outside = Files.writeString(root.getParent().resolve("outside-" + System.nanoTime() + ".txt"), "x");
        try {
            assertThatThrownBy(() -> storage.delete("/uploads/../" + outside.getFileName()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete("https://evil.example/uploads/x.png"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(Files.exists(outside)).as("file bên ngoài không bị đụng tới").isTrue();
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
