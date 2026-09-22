package com.aloute.config;

import com.aloute.auth.FirebaseTokenVerifier;
import com.aloute.auth.SocialIdentity;
import com.aloute.auth.SocialTokenVerifier;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Khởi tạo Firebase khi có file service account. Không có thì ứng dụng vẫn chạy đầy đủ,
 * chỉ tắt đăng nhập Social (và dùng lưu trữ cục bộ thay vì Firebase Storage).
 */
@Configuration
public class FirebaseConfig {

    /** Trả về FirebaseApp dùng chung (khởi tạo lần đầu khi được gọi), hoặc rỗng nếu chưa cấu hình. */
    public static synchronized Optional<FirebaseApp> firebaseApp(AlouteProperties props) {
        if (!props.firebase().enabled()) {
            return Optional.empty();
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return Optional.of(FirebaseApp.getInstance());
        }
        try (InputStream in = new FileInputStream(props.firebase().credentials())) {
            FirebaseOptions.Builder options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in));
            String bucket = props.firebase().storageBucket();
            if (bucket != null && !bucket.isBlank()) {
                options.setStorageBucket(bucket);
            }
            return Optional.of(FirebaseApp.initializeApp(options.build()));
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file Firebase credentials: "
                    + props.firebase().credentials(), e);
        }
    }

    @Bean
    public SocialTokenVerifier socialTokenVerifier(AlouteProperties props) {
        return firebaseApp(props)
                .<SocialTokenVerifier>map(app -> new FirebaseTokenVerifier(FirebaseAuth.getInstance(app)))
                .orElseGet(DisabledVerifier::new);
    }

    private static final class DisabledVerifier implements SocialTokenVerifier {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public SocialIdentity verify(String idToken) {
            throw new InvalidSocialTokenException("Firebase chưa được cấu hình", null);
        }
    }
}
