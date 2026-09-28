package com.aloute.security;

import com.aloute.config.AlouteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Chốt chặn khi chạy profile {@code prod}: từ chối khởi động nếu cấu hình có thể làm lộ hệ thống
 * (khóa JWT dev/quá yếu, cookie không Secure, tài khoản demo, mật khẩu admin yếu...) hoặc không lưu được ảnh.
 * Thà không chạy còn hơn chạy sai.
 */
@Component
@Profile("prod")
public class ProdSafetyCheck {

    private static final Logger log = LoggerFactory.getLogger(ProdSafetyCheck.class);

    static final int MIN_SECRET_BYTES = 32;
    static final int MIN_SEED_PASSWORD_LENGTH = 12;
    private static final String DEV_SEED_PASSWORD = "Aloute@123";

    public ProdSafetyCheck(AlouteProperties props, Environment env) {
        List<String> problems = problems(props, env.getProperty("spring.datasource.password", ""));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Cấu hình production chưa an toàn, ứng dụng từ chối khởi động:\n - "
                    + String.join("\n - ", problems));
        }
        if ("localhost".equals(env.getProperty("spring.mail.host"))) {
            log.warn("spring.mail.host đang là localhost: email quên mật khẩu sẽ KHÔNG gửi được. Đặt MAIL_HOST/MAIL_USER/MAIL_PASSWORD.");
        }
        if (props.firebase().webConfigured() && !props.firebase().enabled()) {
            log.warn("Đã có FIREBASE_WEB_* nhưng thiếu FIREBASE_CREDENTIALS: nút đăng nhập Google/Facebook vẫn bị tắt.");
        }
        if (props.storage().useCloudinary()) {
            log.info("Ảnh/video người dùng lưu trên Cloudinary (cloud: {}).", props.storage().cloudinary().cloudName());
        } else {
            log.info("Ảnh người dùng lưu tại {} (production: phải là Docker volume để không mất khi tạo lại container).",
                    Path.of(props.storage().localDir()).toAbsolutePath().normalize());
        }
    }

    /** Danh sách vấn đề (rỗng nghĩa là ổn). Tách riêng để test được mà không phải khởi động cả ứng dụng. */
    static List<String> problems(AlouteProperties props, String dbPassword) {
        List<String> problems = new ArrayList<>();

        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add("ALOUTE_JWT_SECRET phải có ít nhất " + MIN_SECRET_BYTES + " ký tự (gợi ý: openssl rand -base64 48)");
        } else if (secret.contains("change-me") || secret.startsWith("dev-only") || secret.startsWith("test-only")) {
            problems.add("ALOUTE_JWT_SECRET đang là khóa mẫu/dev, hãy sinh khóa ngẫu nhiên riêng");
        }

        if (!props.cookie().secure()) {
            problems.add("ALOUTE_COOKIE_SECURE phải là true (cookie đăng nhập chỉ được gửi qua HTTPS)");
        }

        String baseUrl = props.baseUrl();
        if (baseUrl == null || !baseUrl.startsWith("https://")) {
            problems.add("ALOUTE_BASE_URL phải bắt đầu bằng https:// (dùng để tạo link trong email)");
        }

        if (dbPassword == null || dbPassword.isBlank()) {
            problems.add("DB_PASSWORD đang trống");
        }

        if (props.seed().demoAccounts()) {
            problems.add("ALOUTE_SEED_DEMO phải là false (không tạo tài khoản mẫu trên production)");
        }
        String seedPassword = props.seed().password();
        if (seedPassword != null && !seedPassword.isBlank()
                && (seedPassword.length() < MIN_SEED_PASSWORD_LENGTH || DEV_SEED_PASSWORD.equals(seedPassword))) {
            problems.add("ALOUTE_SEED_PASSWORD (mật khẩu admin khởi tạo) phải dài ít nhất " + MIN_SEED_PASSWORD_LENGTH
                    + " ký tự và không được là mật khẩu mẫu");
        }

        // Firebase chỉ để đăng nhập Social; để trống là tắt tính năng, còn đã đặt thì file phải đọc được
        String credentials = props.firebase().credentials();
        if (credentials != null && !credentials.isBlank() && !isReadableFile(credentials)) {
            problems.add("FIREBASE_CREDENTIALS không trỏ tới file đọc được (đường dẫn phải là đường dẫn TRONG container;"
                    + " hoặc để trống để tắt đăng nhập Google/Facebook)");
        }

        if (props.storage().useCloudinary()
                && (props.storage().cloudinary() == null || !props.storage().cloudinary().configured())) {
            problems.add("ALOUTE_STORAGE_TYPE=cloudinary nhưng thiếu CLOUDINARY_CLOUD_NAME/CLOUDINARY_API_KEY/CLOUDINARY_API_SECRET");
        }
        // Ngay cả khi dùng Cloudinary, CloudinaryStorageService vẫn lưu tạm xuống đây khi Cloudinary từ chối
        // yêu cầu (hết hạn mức, sai quyền...) nên thư mục này luôn phải ghi được, không chỉ khi type=local.
        String uploadProblem = uploadDirProblem(props.storage().localDir());
        if (uploadProblem != null) {
            problems.add(uploadProblem);
        }
        return problems;
    }

    private static boolean isReadableFile(String path) {
        try {
            return Files.isReadable(Path.of(path)) && Files.isRegularFile(Path.of(path));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** Lỗi quyền của volume là sự cố hay gặp nhất: phát hiện ngay lúc khởi động thay vì khi người dùng đổi avatar. */
    private static String uploadDirProblem(String localDir) {
        try {
            Path dir = Path.of(localDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            if (!Files.isWritable(dir)) {
                return "Thư mục lưu ảnh " + dir + " không ghi được (kiểm tra quyền của volume, container chạy bằng uid 1000)";
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return "Không tạo/ghi được thư mục lưu ảnh " + localDir + ": " + e.getMessage();
        }
    }
}
