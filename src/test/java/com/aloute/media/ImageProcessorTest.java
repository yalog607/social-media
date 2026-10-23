package com.aloute.media;

import com.aloute.exception.media.InvalidMediaException;
import com.aloute.util.media.ImageProcessor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    // ---------- Dữ liệu thử ----------

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    private static BufferedImage solid(int w, int h, Color color) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    /** Ảnh w×h, góc trên-trái (một phần tư) màu đỏ, phần còn lại xanh: đủ để biết ảnh đã bị lật/xoay thế nào. */
    private static BufferedImage quadrants(int w, int h) {
        BufferedImage image = solid(w, h, Color.BLUE);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h / 2);
        g.dispose();
        return image;
    }

    private static boolean isRed(int rgb) {
        return ((rgb >> 16) & 0xFF) > 170 && ((rgb >> 8) & 0xFF) < 100 && (rgb & 0xFF) < 100;
    }

    private static boolean isBlue(int rgb) {
        return (rgb & 0xFF) > 170 && ((rgb >> 16) & 0xFF) < 100 && ((rgb >> 8) & 0xFF) < 100;
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    /** Chèn vào sau SOI một đoạn COM chứa {@code secret} và một đoạn APP1/EXIF chỉ có thẻ Orientation. */
    private static byte[] jpegWithExif(byte[] jpeg, int orientation, String secret) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);

        byte[] comment = secret.getBytes(StandardCharsets.UTF_8);
        out.write(0xFF);
        out.write(0xFE);
        writeShort(out, comment.length + 2);
        out.writeBytes(comment);

        byte[] tiff = {
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,                       // tiêu đề TIFF, IFD0 ở offset 8
                0x00, 0x01,                                                         // 1 mục
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00, // Orientation = SHORT
                0x00, 0x00, 0x00, 0x00                                              // không có IFD kế tiếp
        };
        out.write(0xFF);
        out.write(0xE1);
        writeShort(out, 2 + 6 + tiff.length);
        out.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        out.writeBytes(tiff);

        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] pngHeaderClaiming(int width, int height) {
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        ihdr.writeBytes(new byte[]{'I', 'H', 'D', 'R'});
        for (int v : new int[]{width, height}) {
            ihdr.writeBytes(new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v});
        }
        ihdr.writeBytes(new byte[]{8, 2, 0, 0, 0}); // 8 bit, RGB
        CRC32 crc = new CRC32();
        crc.update(ihdr.toByteArray());

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        png.writeBytes(new byte[]{0, 0, 0, 13});
        png.writeBytes(ihdr.toByteArray());
        long c = crc.getValue();
        png.writeBytes(new byte[]{(byte) (c >>> 24), (byte) (c >>> 16), (byte) (c >>> 8), (byte) c});
        return png.toByteArray();
    }

    // ---------- Thu nhỏ ----------

    @Test
    void shrinksLargeImagesKeepingAspectRatio() throws IOException {
        var result = ImageProcessor.process(encode(solid(3000, 2000, Color.GREEN), "jpg"));

        assertThat(result.width()).isEqualTo(1920);
        assertThat(result.height()).isEqualTo(1280);
        BufferedImage decoded = decode(result.data());
        assertThat(decoded.getWidth()).isEqualTo(1920);
        assertThat(decoded.getHeight()).isEqualTo(1280);
        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(result.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void shrinksTallImagesByTheirLongestSide() throws IOException {
        var result = ImageProcessor.process(encode(solid(1000, 3000, Color.GREEN), "jpg"));

        assertThat(result.height()).isEqualTo(1920);
        assertThat(result.width()).isEqualTo(640);
    }

    @Test
    void veryWideImagesAreSubsampledThenScaledToTheLimit() throws IOException {
        var result = ImageProcessor.process(encode(solid(4000, 100, Color.GREEN), "jpg"));

        assertThat(result.width()).isEqualTo(1920);
        assertThat(result.height()).isEqualTo(48);
    }

    @Test
    void leavesSmallImagesAtTheirSize() throws IOException {
        var result = ImageProcessor.process(encode(solid(200, 100, Color.GREEN), "jpg"));

        assertThat(result.width()).isEqualTo(200);
        assertThat(result.height()).isEqualTo(100);
    }

    // ---------- Hướng xoay EXIF ----------

    @ParameterizedTest(name = "EXIF {0}: {1}x{2}, vùng đỏ ở góc {3}")
    @CsvSource({
            "1, 64, 32, TL",
            "2, 64, 32, TR",
            "3, 64, 32, BR",
            "4, 64, 32, BL",
            "5, 32, 64, TL",
            "6, 32, 64, TR",
            "7, 32, 64, BR",
            "8, 32, 64, BL",
    })
    void appliesExifOrientationIntoThePixels(int orientation, int expectedWidth, int expectedHeight, String corner)
            throws IOException {
        byte[] jpeg = jpegWithExif(encode(quadrants(64, 32), "jpg"), orientation, "khong-quan-trong");

        var result = ImageProcessor.process(jpeg);
        BufferedImage out = decode(result.data());

        assertThat(out.getWidth()).isEqualTo(expectedWidth);
        assertThat(out.getHeight()).isEqualTo(expectedHeight);
        int w = out.getWidth();
        int h = out.getHeight();
        int redX = corner.endsWith("L") ? w / 4 : 3 * w / 4;
        int redY = corner.startsWith("T") ? h / 4 : 3 * h / 4;
        int blueX = corner.endsWith("L") ? 3 * w / 4 : w / 4;
        int blueY = corner.startsWith("T") ? 3 * h / 4 : h / 4;
        assertThat(isRed(out.getRGB(redX, redY))).as("góc %s phải đỏ", corner).isTrue();
        assertThat(isBlue(out.getRGB(blueX, blueY))).as("góc đối diện phải xanh").isTrue();
    }

    // ---------- Xóa metadata ----------

    @Test
    void removesExifAndCommentsIncludingLocationData() throws IOException {
        String secret = "GPS-LATITUDE-10.762622-LONGITUDE-106.660172";
        byte[] jpeg = jpegWithExif(encode(solid(100, 100, Color.GREEN), "jpg"), 1, secret);
        assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).contains("Exif").contains(secret);

        byte[] cleaned = ImageProcessor.process(jpeg).data();

        String asText = new String(cleaned, StandardCharsets.ISO_8859_1);
        assertThat(asText).doesNotContain("Exif").doesNotContain("GPS-LATITUDE").doesNotContain("106.660172");
    }

    // ---------- PNG / GIF / WEBP ----------

    @Test
    void keepsTransparencyInPng() throws IOException {
        BufferedImage source = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 40; y++) {
                source.setRGB(x, y, 0xFFFF0000);
            }
        }
        source.setRGB(0, 0, 0x00000000);

        var result = ImageProcessor.process(encode(source, "png"));

        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.contentType()).isEqualTo("image/png");
        BufferedImage decoded = decode(result.data());
        assertThat(decoded.getColorModel().hasAlpha()).isTrue();
        assertThat(decoded.getRGB(0, 0) >>> 24).as("điểm trong suốt vẫn trong suốt").isZero();
        assertThat(decoded.getRGB(5, 5) >>> 24).isEqualTo(255);
    }

    @Test
    void gifAndWebpAreStoredUnchanged() {
        byte[] gif = "GIF89a-fake-animation-bytes".getBytes(StandardCharsets.ISO_8859_1);
        byte[] webp = "RIFF....WEBPVP8 -fake".getBytes(StandardCharsets.ISO_8859_1);

        var g = ImageProcessor.process(gif);
        var w = ImageProcessor.process(webp);

        assertThat(g.data()).isSameAs(gif);
        assertThat(g.extension()).isEqualTo("gif");
        assertThat(g.contentType()).isEqualTo("image/gif");
        assertThat(w.data()).isSameAs(webp);
        assertThat(w.extension()).isEqualTo("webp");
    }

    // ---------- Từ chối ----------

    @Test
    void rejectsPixelBombsBeforeDecodingThem() {
        byte[] bomb = pngHeaderClaiming(10_000, 10_000);

        assertThatThrownBy(() -> ImageProcessor.process(bomb))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("quá lớn");
    }

    @Test
    void acceptsImagesJustUnderThePixelLimit() {
        byte[] header = pngHeaderClaiming(6_000, 6_000); // 36 triệu điểm ảnh < 40 triệu

        // Qua được kiểm tra kích thước; không có dữ liệu điểm ảnh nên phải báo "không đọc được", không phải "quá lớn"
        assertThatThrownBy(() -> ImageProcessor.process(header))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("Không đọc được");
    }

    @Test
    void rejectsNonImagesEmptyAndBrokenFiles() {
        assertThatThrownBy(() -> ImageProcessor.process("xin chào".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidMediaException.class).hasMessageContaining("JPG, PNG");
        assertThatThrownBy(() -> ImageProcessor.process(new byte[0]))
                .isInstanceOf(InvalidMediaException.class);
        byte[] brokenJpeg = new byte[300];
        brokenJpeg[0] = (byte) 0xFF;
        brokenJpeg[1] = (byte) 0xD8;
        brokenJpeg[2] = (byte) 0xFF;
        assertThatThrownBy(() -> ImageProcessor.process(brokenJpeg))
                .isInstanceOf(InvalidMediaException.class).hasMessageContaining("Không đọc được");
    }

    @Test
    void rejectsSvgEvenWhenItLooksLikeAnImage() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ImageProcessor.process(svg)).isInstanceOf(InvalidMediaException.class);
    }
}
