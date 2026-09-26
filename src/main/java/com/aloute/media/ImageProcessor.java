package com.aloute.media;

import com.aloute.storage.ImageSniffer;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Xử lý ảnh đăng lên: kiểm tra định dạng thật, chặn ảnh quá nhiều điểm ảnh TRƯỚC khi giải mã, thu nhỏ,
 * áp dụng hướng xoay EXIF vào điểm ảnh rồi mã hóa lại. Mã hóa lại đồng nghĩa mọi metadata (EXIF, GPS, chú thích) bị xóa.
 * GIF (có thể là ảnh động) và WEBP được giữ nguyên.
 */
public final class ImageProcessor {

    public record ProcessedImage(byte[] data, String extension, String contentType, int width, int height) {
    }

    private static final float JPEG_QUALITY = 0.85f;

    private ImageProcessor() {
    }

    public static ProcessedImage process(byte[] input) {
        if (input == null || input.length == 0) {
            throw new InvalidMediaException("File ảnh đang trống.");
        }
        ImageSniffer.Detected type = ImageSniffer.detect(input)
                .orElseThrow(() -> new InvalidMediaException("Chỉ nhận ảnh JPG, PNG, GIF hoặc WEBP."));

        if (type.extension().equals("gif") || type.extension().equals("webp")) {
            return new ProcessedImage(input, type.extension(), type.contentType(), 0, 0);
        }

        BufferedImage image = decodeWithinLimits(input);
        if (type.extension().equals("jpg")) {
            image = applyOrientation(image, readOrientation(input));
        }
        byte[] encoded = type.extension().equals("jpg") ? encodeJpeg(image) : encodePng(image);
        return new ProcessedImage(encoded, type.extension(), type.contentType(), image.getWidth(), image.getHeight());
    }

    // ---------- Giải mã ----------

    private static BufferedImage decodeWithinLimits(byte[] input) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MediaLimits.MAX_PIXELS) {
                    throw new InvalidMediaException("Ảnh quá lớn về kích thước (tối đa "
                            + MediaLimits.MAX_PIXELS / 1_000_000 + " triệu điểm ảnh).");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                // Ảnh rất lớn thì giải mã ở độ phân giải thấp hơn ngay từ đầu để đỡ tốn bộ nhớ
                int step = Math.max(1, Math.max(width, height) / MediaLimits.MAX_DIMENSION);
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return shrink(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        } catch (InvalidMediaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw unreadable();
        }
    }

    private static InvalidMediaException unreadable() {
        return new InvalidMediaException("Không đọc được ảnh này, hãy thử ảnh khác.");
    }

    private static BufferedImage shrink(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MediaLimits.MAX_DIMENSION) {
            return source;
        }
        double scale = (double) MediaLimits.MAX_DIMENSION / longest;
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage target = new BufferedImage(newWidth, newHeight, typeFor(source));
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return target;
    }

    private static int typeFor(BufferedImage image) {
        return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    // ---------- Hướng xoay EXIF ----------

    private static int readOrientation(byte[] jpeg) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(jpeg));
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (ImageProcessingException | IOException | MetadataException | RuntimeException e) {
            // Không đọc được EXIF thì coi như ảnh đã đúng chiều
        }
        return 1;
    }

    /**
     * Đưa ảnh về đúng chiều hiển thị theo giá trị Orientation của EXIF (2..8), vì sau khi xóa EXIF trình duyệt
     * không còn biết phải xoay. Ánh xạ từng điểm ảnh, dễ kiểm chứng và chỉ chạy trên ảnh đã thu nhỏ.
     */
    private static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, typeFor(source));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = source.getRGB(x, y);
                switch (orientation) {
                    case 2 -> target.setRGB(w - 1 - x, y, pixel);         // lật ngang
                    case 3 -> target.setRGB(w - 1 - x, h - 1 - y, pixel); // xoay 180°
                    case 4 -> target.setRGB(x, h - 1 - y, pixel);         // lật dọc
                    case 5 -> target.setRGB(y, x, pixel);                 // chuyển vị
                    case 6 -> target.setRGB(h - 1 - y, x, pixel);         // xoay 90° theo chiều kim đồng hồ
                    case 7 -> target.setRGB(h - 1 - y, w - 1 - x, pixel); // chuyển vị ngược
                    default -> target.setRGB(y, w - 1 - x, pixel);        // 8: xoay 90° ngược chiều kim đồng hồ
                }
            }
        }
        return target;
    }

    // ---------- Mã hóa ----------

    private static byte[] encodeJpeg(BufferedImage image) {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE); // JPEG không có kênh alpha
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(rgb, null, null), param); // metadata = null: không ghi EXIF
            stream.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new InvalidMediaException("Không xử lý được ảnh này, hãy thử ảnh khác.");
        } finally {
            writer.dispose();
        }
    }

    private static byte[] encodePng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", out)) {
                throw new InvalidMediaException("Không xử lý được ảnh này, hãy thử ảnh khác.");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new InvalidMediaException("Không xử lý được ảnh này, hãy thử ảnh khác.");
        }
    }
}
