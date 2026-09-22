package com.aloute.common;

import com.aloute.config.AlouteProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final String from;

    public MailService(JavaMailSender sender, AlouteProperties props) {
        this.sender = sender;
        this.from = props.mail().from();
    }

    /** Gửi email đặt lại mật khẩu. Lỗi gửi chỉ được ghi log để không lộ việc email có tồn tại hay không. */
    public void sendPasswordReset(String to, String displayName, String link, int validMinutes) {
        String name = HtmlUtils.htmlEscape(displayName);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto">
                  <h2>Xin chào %s 👋</h2>
                  <p>Bạn vừa xin đặt lại mật khẩu ALOUTE. Bấm nút dưới đây (link dùng được trong %d phút, chỉ một lần):</p>
                  <p><a href="%s" style="display:inline-block;padding:12px 20px;background:#FFE45E;color:#16121F;
                     border:2px solid #16121F;border-radius:10px;font-weight:bold;text-decoration:none">Đặt lại mật khẩu</a></p>
                  <p style="color:#666;font-size:13px">Nếu không phải bạn yêu cầu, cứ bỏ qua email này — mật khẩu vẫn an toàn.</p>
                </div>
                """.formatted(name, validMinutes, HtmlUtils.htmlEscape(link));
        send(to, "Đặt lại mật khẩu ALOUTE", html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            log.warn("Không gửi được email tới {}: {}", to, e.getMessage());
        }
    }
}
