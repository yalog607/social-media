package com.aloute.util.storage;

import com.aloute.service.storage.StorageService;
import com.aloute.util.storage.ImageSniffer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageSnifferTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Test
    void recognizesRealImagesByMagicBytes() {
        assertThat(ImageSniffer.sniff(PNG).extension()).isEqualTo("png");
        assertThat(ImageSniffer.sniff(JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(ImageSniffer.sniff("GIF89a....".getBytes(StandardCharsets.US_ASCII)).extension()).isEqualTo("gif");
        assertThat(ImageSniffer.sniff("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)).extension())
                .isEqualTo("webp");
    }

    @Test
    void rejectsSvgHtmlAndPlainTextEvenIfNamedLikeImages() {
        assertThatThrownBy(() -> ImageSniffer.sniff("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(StorageService.InvalidUploadException.class);
        assertThatThrownBy(() -> ImageSniffer.sniff("<html>hi</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(StorageService.InvalidUploadException.class);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> ImageSniffer.sniff(new byte[0]))
                .isInstanceOf(StorageService.InvalidUploadException.class);
        byte[] big = new byte[(int) ImageSniffer.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> ImageSniffer.sniff(big))
                .isInstanceOf(StorageService.InvalidUploadException.class)
                .hasMessageContaining("5 MB");
    }
}
