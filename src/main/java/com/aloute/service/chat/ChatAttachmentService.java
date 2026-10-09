package com.aloute.service.chat;

import com.aloute.exception.chat.ChatActionException;
import com.aloute.model.chat.AttachmentKind;
import com.aloute.util.chat.ChatLimits;

import com.aloute.util.media.ImageProcessor;
import com.aloute.exception.media.InvalidMediaException;
import com.aloute.util.media.VideoSniffer;
import com.aloute.util.storage.ImageSniffer;
import com.aloute.service.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lưu file đính kèm tin nhắn. Ảnh được xử lý qua {@link ImageProcessor} như bài đăng (xóa metadata, thu nhỏ);
 * video và file khác được lưu nguyên vẹn. Loại được nhận diện bằng chữ ký byte, không tin Content-Type của client.
 */
@Service
public class ChatAttachmentService {

    private static final String FOLDER = "chat";
    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,5}");
    private static final String DEFAULT_EXTENSION = "bin";

    record Stored(AttachmentKind kind, String url, String contentType, long sizeBytes, String originalName) {
    }

    private final StorageService storage;

    public ChatAttachmentService(StorageService storage) {
        this.storage = storage;
    }

    /** @return null nếu {@code file} rỗng hoặc không có file gửi kèm */
    Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > ChatLimits.MAX_ATTACHMENT_BYTES) {
            throw new ChatActionException("File đính kèm tối đa " + (ChatLimits.MAX_ATTACHMENT_BYTES / 1024 / 1024) + " MB.");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ChatActionException("Không đọc được file, hãy thử lại.");
        }
        String originalName = safeOriginalName(file.getOriginalFilename());

        if (ImageSniffer.detect(data).isPresent()) {
            try {
                ImageProcessor.ProcessedImage processed = ImageProcessor.process(data);
                String url = storage.storeBytes(processed.data(), FOLDER, processed.extension());
                return new Stored(AttachmentKind.IMAGE, url, processed.contentType(), processed.data().length, originalName);
            } catch (InvalidMediaException e) {
                throw new ChatActionException(e.getMessage());
            }
        }
        Optional<VideoSniffer.VideoType> video = VideoSniffer.sniff(data);
        if (video.isPresent()) {
            String url = storage.storeBytes(data, FOLDER, video.get().extension());
            return new Stored(AttachmentKind.VIDEO, url, video.get().contentType(), data.length, originalName);
        }

        String extension = extensionOf(originalName);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String url = storage.storeBytes(data, FOLDER, extension);
        return new Stored(AttachmentKind.FILE, url, contentType, data.length, originalName);
    }

    private static String safeOriginalName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        // Chỉ giữ phần tên (bỏ đường dẫn nếu trình duyệt lỡ gửi kèm) và cắt bớt nếu quá dài
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).strip();
        return base.isEmpty() ? "file" : base.length() > 255 ? base.substring(0, 255) : base;
    }

    private static String extensionOf(String originalName) {
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return DEFAULT_EXTENSION;
        }
        String candidate = originalName.substring(dot + 1).toLowerCase();
        return SAFE_EXTENSION.matcher(candidate).matches() ? candidate : DEFAULT_EXTENSION;
    }
}
