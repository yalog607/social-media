package com.aloute.security;

import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy server thật với application-prod.yml thật, để chứng minh: sau Caddy, giới hạn đăng nhập sai
 * tính theo IP người dùng thật (header X-Client-IP do Caddy đặt) chứ không phải IP container caddy.
 * Đồng thời ProdSafetyCheck (chỉ bật ở profile prod) phải cho phép cấu hình an toàn dưới đây khởi động.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "aloute.jwt.secret=Zx9fQ2mVb7Lk0RtYw3NpHs8DcJaE5uGiO1XyBvMqTn4KrWdUeCzA",
        "aloute.cookie.secure=true",
        "aloute.base-url=https://aloute.example",
})
@ActiveProfiles({"test", "prod"})
class ForwardedHeadersIT extends IntegrationTest {

    @LocalServerPort int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Kết quả một lần gửi form đăng nhập. */
    private record Reply(int status, String body, String location, String setCookies) {
    }

    private static String randomClientIp() {
        int n = Math.abs(UUID.randomUUID().hashCode());
        return "203.0.113." + (n % 250 + 1); // dải tài liệu TEST-NET-3, không phải IP thật
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String cookieValue(HttpResponse<?> response, String name) {
        return response.headers().allValues("set-cookie").stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(name.length() + 1, c.indexOf(';')))
                .findFirst().orElseThrow();
    }

    private Reply login(String identifier, String password, String... extraHeaders) throws Exception {
        URI login = URI.create("http://localhost:" + port + "/login");
        HttpRequest.Builder page = HttpRequest.newBuilder(login).GET();
        for (int i = 0; i < extraHeaders.length; i += 2) {
            page.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        String csrf = cookieValue(send(page.build()), "XSRF-TOKEN");

        String form = "identifier=" + URLEncoder.encode(identifier, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest.Builder post = HttpRequest.newBuilder(login)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(form));
        for (int i = 0; i < extraHeaders.length; i += 2) {
            post.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        HttpResponse<String> response = send(post.build());
        return new Reply(response.statusCode(), response.body(),
                response.headers().firstValue("location").orElse(""),
                String.join("\n", response.headers().allValues("set-cookie")));
    }

    @Test
    void failedLoginsAreCountedPerRealClientIpBehindTheProxy() throws Exception {
        String attacker = randomClientIp();
        String innocent = randomClientIp();
        String identifier = "victim" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";

        for (int i = 0; i < 3; i++) {
            assertThat(login(identifier, "Sai12345", "X-Client-IP", attacker).body())
                    .contains("chưa đúng");
        }

        assertThat(login(identifier, "Sai12345", "X-Client-IP", attacker).body())
                .as("IP kẻ tấn công bị chặn sau 3 lần sai")
                .contains("thử sai nhiều lần");
        assertThat(login(identifier, "Sai12345", "X-Client-IP", innocent).body())
                .as("người dùng khác (IP khác) vẫn đăng nhập bình thường, không bị vạ lây")
                .contains("chưa đúng").doesNotContain("thử sai nhiều lần");
    }

    @Test
    void spoofedXForwardedForCannotEscapeTheLimit() throws Exception {
        String attacker = randomClientIp();
        String identifier = "victim" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        for (int i = 0; i < 3; i++) {
            login(identifier, "Sai12345", "X-Client-IP", attacker);
        }

        Reply retry = login(identifier, "Sai12345", "X-Client-IP", attacker,
                "X-Forwarded-For", "198.51.100." + (1 + (int) (Math.random() * 200)));

        assertThat(retry.body()).contains("thử sai nhiều lần");
    }

    @Test
    void appDoesNotTrustCloudflareHeaderItself_onlyCaddyMayReadIt() throws Exception {
        // CF-Connecting-IP là việc của Caddy (kiểm tra kết nối đến từ dải IP Cloudflare rồi mới đọc). App bỏ qua nó,
        // nên hai giá trị khác nhau vẫn rơi vào cùng một nhóm (kết nối trực tiếp) và bị tính chung.
        String identifier = "cf" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        for (int i = 0; i < 3; i++) {
            login(identifier, "Sai12345", "CF-Connecting-IP", randomClientIp());
        }

        Reply retry = login(identifier, "Sai12345", "CF-Connecting-IP", randomClientIp());

        assertThat(retry.body()).as("đổi CF-Connecting-IP không giúp né giới hạn").contains("thử sai nhiều lần");
    }

    @Test
    void requestsWithoutTheProxyHeaderShareTheDirectConnectionBucket() throws Exception {
        // Không có X-Client-IP thì dùng IP kết nối trực tiếp (loopback trong test); không được "chết" hay lẫn với IP khác
        String identifier = "direct" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        assertThat(login(identifier, "Sai12345").body()).contains("chưa đúng");
    }

    @Test
    void forwardedProtoMakesRedirectsAndCookiesSecureHttps() throws Exception {
        User user = createUser();

        Reply reply = login(user.getEmail(), PASSWORD,
                "X-Client-IP", randomClientIp(), "X-Forwarded-Proto", "https");

        assertThat(reply.status()).isEqualTo(302);
        assertThat(reply.location()).as("redirect phải giữ https, không tụt về http").startsWith("https://");
        assertThat(reply.setCookies()).contains("ALOUTE_TOKEN=").contains("Secure").contains("HttpOnly");
    }
}
