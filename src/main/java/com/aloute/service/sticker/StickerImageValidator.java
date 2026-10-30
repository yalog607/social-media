package com.aloute.service.sticker;

import com.aloute.exception.admin.InvalidAdminActionException;
import com.aloute.util.storage.ImageSniffer;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Decode bounded, static images before storing any bytes. Never crops or flattens transparency. */
public final class StickerImageValidator {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public record Image(byte[] bytes, String extension) {}
    private StickerImageValidator() {}
    public static Image validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid("Mỗi ảnh phải từ 1 byte đến 10 MB.");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw invalid("Ảnh quá lớn, tối đa 10 MB.");
            var type = ImageSniffer.detect(bytes).orElseThrow(() -> invalid("Chỉ nhận PNG hoặc WebP tĩnh."));
            if (!type.extension().equals("png") && !type.extension().equals("webp")) throw invalid("Chỉ nhận PNG hoặc WebP tĩnh.");
            rejectAnimation(bytes, type.extension());
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid("Không đọc được ảnh.");
                var reader = readers.next();
                try {
                    reader.setInput(stream, true, true);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (w < 1 || h < 1 || w > 2048 || h > 2048) throw invalid("Mỗi chiều ảnh tối đa 2048 px; khuyến nghị 512×512 px.");
                    if (reader.read(0) == null) throw invalid("Ảnh bị hỏng.");
                } finally { reader.dispose(); }
            }
            return new Image(bytes, type.extension());
        } catch (InvalidAdminActionException e) { throw e; }
        catch (Exception e) { throw invalid("Ảnh bị hỏng hoặc không đọc được."); }
    }
    private static void rejectAnimation(byte[] b, String type) {
        int offset = type.equals("png") ? 8 : 12;
        if (type.equals("webp") && (b.length < 12 || Integer.toUnsignedLong(ByteBuffer.wrap(b, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()) + 8 != b.length))
            throw invalid("WebP bị hỏng.");
        while (offset < b.length) {
            if (b.length - offset < (type.equals("png") ? 12 : 8)) throw invalid("Ảnh bị hỏng.");
            boolean png = type.equals("png");
            int length = ByteBuffer.wrap(b, offset + (png ? 0 : 4), 4).order(png ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN).getInt();
            String chunk = new String(b, offset + (png ? 4 : 0), 4, StandardCharsets.US_ASCII);
            if (length < 0 || (long) offset + length + (png ? 12 : 8) > b.length) throw invalid("Ảnh bị hỏng.");
            if (chunk.equals("acTL") || chunk.equals("ANIM") || chunk.equals("ANMF")
                    || (chunk.equals("VP8X") && length > 0 && (b[offset + 8] & 2) != 0)) throw invalid("Chưa hỗ trợ ảnh động. Hãy chọn PNG hoặc WebP tĩnh.");
            offset += length + (png ? 12 : 8 + (length & 1));
        }
    }
    private static InvalidAdminActionException invalid(String message) { return new InvalidAdminActionException(message); }
}
