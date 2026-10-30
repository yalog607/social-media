package com.aloute.service.common;

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

    /**
     * Gửi email thông báo chế tài xử phạt (cảnh cáo, khóa tài khoản, gỡ nội dung).
     * Lỗi gửi chỉ được ghi log để không làm hỏng giao dịch xử lý báo cáo.
     */
    public void sendSanctionNotification(String to, String displayName, String actionName, String targetType,
                                         String preview, String reason) {
        String name = HtmlUtils.htmlEscape(displayName);
        String actionLabel = switch (actionName) {
            case "WARN" -> "Cảnh cáo vi phạm Tiêu chuẩn cộng đồng";
            case "SUSPEND" -> "Khóa tài khoản tạm thời";
            case "REMOVE_CONTENT" -> "Gỡ bỏ nội dung vi phạm";
            default -> "Xử lý vi phạm";
        };
        String targetLabel = switch (targetType) {
            case "POST" -> "bài viết";
            case "COMMENT" -> "bình luận";
            case "USER" -> "tài khoản";
            default -> "nội dung";
        };
        String reasonHtml = (reason != null && !reason.isBlank())
                ? "<p><strong>Lý do từ đội ngũ kiểm duyệt:</strong> " + HtmlUtils.htmlEscape(reason) + "</p>"
                : "";
        String previewHtml = (preview != null && !preview.isBlank())
                ? "<blockquote style=\"margin:12px 0;padding:10px 14px;background:#F9F9F9;border-left:4px solid #D9383A;color:#333;font-style:italic;\">"
                  + HtmlUtils.htmlEscape(preview) + "</blockquote>"
                : "";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:540px;margin:auto;padding:16px;border:1px solid #E5E7EB;border-radius:12px;">
                  <h2 style="color:#D9383A;margin-top:0;">Thông báo xử lý vi phạm ALOUTE</h2>
                  <p>Xin chào <strong>%s</strong>,</p>
                  <p>Chúng tôi gửi thông báo này để thông tin rằng %s của bạn đã bị áp dụng biện pháp xử lý: <strong>%s</strong>.</p>
                  %s
                  %s
                  <p style="color:#555;font-size:14px;margin-top:20px;">
                    Nếu bạn cho rằng đây là sự nhầm lẫn, bạn có thể tạo phiếu yêu cầu hỗ trợ và khiếu nại tại mục <strong>Hỗ trợ</strong> trên hệ thống ALOUTE.
                  </p>
                  <p style="color:#888;font-size:12px;border-top:1px solid #E5E7EB;padding-top:10px;margin-bottom:0;">
                    Email này được gửi tự động từ hệ thống Kiểm duyệt ALOUTE.
                  </p>
                </div>
                """.formatted(name, targetLabel, actionLabel, previewHtml, reasonHtml);
        send(to, "[ALOUTE] Thông báo về việc " + actionLabel.toLowerCase(), html);
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
