package com.aloute.media;

import com.aloute.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Kiểm tra, xử lý và lưu media của một bài đăng: 0–4 ảnh HOẶC 1 video. Kiểm tra toàn bộ số lượng/dung lượng
 * trước khi xử lý, và nếu có lỗi giữa chừng thì xóa những file đã lưu (không để rác trên đĩa).
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final String FOLDER = "posts";

    public record StoredMedia(MediaKind kind, String url, String contentType, long sizeBytes) {
    }

    private final StorageService storage;

    public MediaService(StorageService storage) {
        this.storage = storage;
    }

    /**
     * @param images các file ảnh (phần tử rỗng bị bỏ qua)
     * @param video  file video, có thể null hoặc rỗng
     * @return media đã lưu theo đúng thứ tự gửi lên; rỗng nếu bài chỉ có chữ
     * @throws InvalidMediaException vi phạm giới hạn hoặc file không hợp lệ
     */
    public List<StoredMedia> storeAll(List<MultipartFile> images, MultipartFile video) {
        List<MultipartFile> imageParts = images == null ? List.of() : images.stream().filter(MediaService::hasContent).toList();
        MultipartFile videoPart = hasContent(video) ? video : null;

        if (!imageParts.isEmpty() && videoPart != null) {
            throw new InvalidMediaException("Mỗi bài chỉ đăng ảnh hoặc video, không đăng cả hai.");
        }
        if (imageParts.size() > MediaLimits.MAX_IMAGES) {
            throw new InvalidMediaException("Mỗi bài tối đa " + MediaLimits.MAX_IMAGES + " ảnh.");
        }
        for (MultipartFile image : imageParts) {
            if (image.getSize() > MediaLimits.MAX_IMAGE_BYTES) {
                throw new InvalidMediaException("Mỗi ảnh tối đa " + megabytes(MediaLimits.MAX_IMAGE_BYTES) + " MB.");
            }
        }
        if (videoPart != null && videoPart.getSize() > MediaLimits.MAX_VIDEO_BYTES) {
            throw new InvalidMediaException("Video tối đa " + megabytes(MediaLimits.MAX_VIDEO_BYTES) + " MB.");
        }

        List<StoredMedia> stored = new ArrayList<>();
        try {
            for (MultipartFile image : imageParts) {
                stored.add(storeImage(image));
            }
            if (videoPart != null) {
                stored.add(storeVideo(videoPart));
            }
            return stored;
        } catch (RuntimeException e) {
            discard(stored);
            throw e;
        }
    }

    /** Xóa file của những media đã lưu (dùng khi tạo bài thất bại). Lỗi xóa chỉ ghi log. */
    public void discard(List<StoredMedia> media) {
        for (StoredMedia item : media) {
            try {
                storage.delete(item.url());
            } catch (RuntimeException e) {
                log.warn("Không xóa được file media {}: {}", item.url(), e.getMessage());
            }
        }
    }

    private StoredMedia storeImage(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidMediaException("Không đọc được file ảnh, hãy thử lại.");
        }
        ImageProcessor.ProcessedImage processed = ImageProcessor.process(bytes);
        String url = storage.storeBytes(processed.data(), FOLDER, processed.extension());
        return new StoredMedia(MediaKind.IMAGE, url, processed.contentType(), processed.data().length);
    }

    private StoredMedia storeVideo(MultipartFile file) {
        try {
            VideoSniffer.VideoType type;
            try (InputStream in = file.getInputStream()) {
                type = VideoSniffer.sniff(in.readNBytes(VideoSniffer.HEAD_BYTES))
                        .orElseThrow(() -> new InvalidMediaException("Chỉ nhận video MP4 hoặc WEBM."));
            }
            try (InputStream in = file.getInputStream()) {
                String url = storage.storeStream(in, FOLDER, type.extension());
                return new StoredMedia(MediaKind.VIDEO, url, type.contentType(), file.getSize());
            }
        } catch (IOException e) {
            throw new InvalidMediaException("Không đọc được file video, hãy thử lại.");
        }
    }

    private static boolean hasContent(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private static long megabytes(long bytes) {
        return bytes / (1024 * 1024);
    }
}
