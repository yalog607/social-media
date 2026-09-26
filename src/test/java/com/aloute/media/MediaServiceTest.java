package com.aloute.media;

import com.aloute.config.AlouteProperties;
import com.aloute.storage.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaServiceTest {

    @TempDir Path root;
    private MediaService media;

    @BeforeEach
    void setUp() {
        var props = new AlouteProperties(null, null, null, null, null,
                new AlouteProperties.Storage(root.toString()), null, null);
        media = new MediaService(new LocalStorageService(props));
    }

    private static byte[] jpeg(int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static MultipartFile image(String name, byte[] content) {
        return new MockMultipartFile("images", name, "image/jpeg", content);
    }

    private static byte[] mp4(int size) {
        byte[] data = new byte[size];
        byte[] head = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(head, 0, data, 0, head.length);
        return data;
    }

    private long storedFileCount() throws IOException {
        Path posts = root.resolve("posts");
        if (!Files.exists(posts)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(posts)) {
            return files.count();
        }
    }

    // ---------- Từ chối ----------

    @Test
    void rejectsMoreThanFourImagesAndStoresNothing() throws IOException {
        byte[] ok = jpeg(50, 50);
        var five = List.of(image("1.jpg", ok), image("2.jpg", ok), image("3.jpg", ok), image("4.jpg", ok), image("5.jpg", ok));

        assertThatThrownBy(() -> media.storeAll(five, null))
                .isInstanceOf(InvalidMediaException.class).hasMessageContaining("tối đa 4 ảnh");
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void rejectsImagesTogetherWithAVideo() throws IOException {
        var images = List.of(image("1.jpg", jpeg(50, 50)));
        var video = new MockMultipartFile("video", "v.mp4", "video/mp4", mp4(100));

        assertThatThrownBy(() -> media.storeAll(images, video))
                .isInstanceOf(InvalidMediaException.class).hasMessageContaining("ảnh hoặc video");
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void rejectsAVideoOverTwentyFiveMegabytes() throws IOException {
        var tooBig = new MockMultipartFile("video", "v.mp4", "video/mp4", mp4((int) MediaLimits.MAX_VIDEO_BYTES + 1));

        assertThatThrownBy(() -> media.storeAll(List.of(), tooBig))
                .isInstanceOf(InvalidMediaException.class).hasMessageContaining("25 MB");
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void acceptsAVideoOfExactlyTheMaximumSize() throws IOException {
        var max = new MockMultipartFile("video", "v.mp4", "video/mp4", mp4((int) MediaLimits.MAX_VIDEO_BYTES));

        assertThat(media.storeAll(List.of(), max)).hasSize(1);
    }

    @Test
    void rejectsAFakeVideoWhateverItsNameOrDeclaredType() throws IOException {
        var fake = new MockMultipartFile("video", "cute.mp4", "video/mp4",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> media.storeAll(List.of(), fake))
                .isInstanceOf(InvalidMediaException.class).hasMessageContaining("MP4 hoặc WEBM");
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void rejectsAnImageOverEightMegabytesBeforeProcessingIt() throws IOException {
        byte[] huge = new byte[(int) MediaLimits.MAX_IMAGE_BYTES + 1];
        huge[0] = (byte) 0xFF;
        huge[1] = (byte) 0xD8;
        huge[2] = (byte) 0xFF;

        assertThatThrownBy(() -> media.storeAll(List.of(image("huge.jpg", huge)), null))
                .isInstanceOf(InvalidMediaException.class).hasMessageContaining("8 MB");
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void removesWhatWasAlreadyStoredWhenALaterImageIsInvalid() throws IOException {
        var images = List.of(image("good.jpg", jpeg(60, 60)),
                image("bad.jpg", "not an image at all".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> media.storeAll(images, null)).isInstanceOf(InvalidMediaException.class);

        assertThat(storedFileCount()).as("ảnh hợp lệ đã lưu trước đó phải bị xóa").isZero();
    }

    // ---------- Chấp nhận ----------

    @Test
    void storesValidImagesInOrderAsProcessedFiles() throws IOException {
        var first = image("a.jpg", jpeg(3000, 2000)); // sẽ bị thu nhỏ
        var second = image("b.jpg", jpeg(80, 40));

        List<MediaService.StoredMedia> stored = media.storeAll(List.of(first, second), null);

        assertThat(stored).hasSize(2);
        assertThat(stored).allSatisfy(m -> {
            assertThat(m.kind()).isEqualTo(MediaKind.IMAGE);
            assertThat(m.contentType()).isEqualTo("image/jpeg");
            assertThat(m.url()).startsWith("/uploads/posts/").endsWith(".jpg");
            Path file = root.resolve(m.url().substring("/uploads/".length()));
            assertThat(Files.size(file)).isEqualTo(m.sizeBytes());
        });
        BufferedImage firstOut = ImageIO.read(root.resolve(stored.get(0).url().substring("/uploads/".length())).toFile());
        assertThat(firstOut.getWidth()).as("ảnh đầu đã được thu nhỏ").isEqualTo(1920);
        BufferedImage secondOut = ImageIO.read(root.resolve(stored.get(1).url().substring("/uploads/".length())).toFile());
        assertThat(secondOut.getWidth()).isEqualTo(80);
    }

    @Test
    void storesAValidVideoByteForByte() throws IOException {
        byte[] content = mp4(200_000);
        content[199_999] = 42;
        var video = new MockMultipartFile("video", "clip.MP4", "application/octet-stream", content);

        List<MediaService.StoredMedia> stored = media.storeAll(List.of(), video);

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).kind()).isEqualTo(MediaKind.VIDEO);
        assertThat(stored.get(0).contentType()).isEqualTo("video/mp4");
        assertThat(stored.get(0).url()).endsWith(".mp4");
        assertThat(stored.get(0).sizeBytes()).isEqualTo(200_000);
        assertThat(Files.readAllBytes(root.resolve(stored.get(0).url().substring("/uploads/".length())))).isEqualTo(content);
    }

    @Test
    void ignoresEmptyFilePartsSoATextOnlyPostHasNoMedia() throws IOException {
        var emptyImage = new MockMultipartFile("images", "", "application/octet-stream", new byte[0]);
        var emptyVideo = new MockMultipartFile("video", "", "application/octet-stream", new byte[0]);

        assertThat(media.storeAll(List.of(emptyImage), emptyVideo)).isEmpty();
        assertThat(media.storeAll(null, null)).isEmpty();
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void discardRemovesStoredFiles() throws IOException {
        List<MediaService.StoredMedia> stored = media.storeAll(List.of(image("a.jpg", jpeg(50, 50))), null);
        assertThat(storedFileCount()).isEqualTo(1);

        media.discard(stored);

        assertThat(storedFileCount()).isZero();
    }
}
