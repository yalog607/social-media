package com.aloute.storage;

import com.aloute.config.AlouteProperties;
import com.aloute.config.FirebaseConfig;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.StorageClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Lưu ảnh lên Firebase Storage, gắn download token để URL truy cập được mà không cần mở quyền đọc công khai. */
@Service
@ConditionalOnProperty(name = "aloute.storage.type", havingValue = "firebase")
public class FirebaseStorageService implements StorageService {

    private final Bucket bucket;

    public FirebaseStorageService(AlouteProperties props) {
        FirebaseApp app = FirebaseConfig.firebaseApp(props).orElseThrow(() -> new IllegalStateException(
                "aloute.storage.type=firebase nhưng chưa đặt FIREBASE_CREDENTIALS"));
        this.bucket = StorageClient.getInstance(app).bucket();
    }

    @Override
    public String storeImage(MultipartFile file, String folder, UUID ownerId) {
        try {
            byte[] data = file.getBytes();
            ImageSniffer.Detected type = ImageSniffer.sniff(data);
            String path = folder + "/" + ownerId + "-" + UUID.randomUUID() + "." + type.extension();
            String token = UUID.randomUUID().toString();
            bucket.create(path, data, type.contentType());
            bucket.get(path).toBuilder()
                    .setMetadata(Map.of("firebaseStorageDownloadTokens", token))
                    .build().update();
            return "https://firebasestorage.googleapis.com/v0/b/" + bucket.getName() + "/o/"
                    + UriUtils.encode(path, StandardCharsets.UTF_8) + "?alt=media&token=" + token;
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file tải lên", e);
        }
    }
}
