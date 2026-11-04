package com.aloute.common;

import com.aloute.config.AlouteProperties;
import com.aloute.service.common.MailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.util.HtmlUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailServiceTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    private final MailService mail = new MailService(sender, new AlouteProperties(
            null, null, null, null, new AlouteProperties.Mail("sender@example.test"), null, null, null));

    @Test
    void resetUsesAloraAndPreservesRecipientNameAndLink() throws Exception {
        when(sender.createMimeMessage()).thenReturn(message);
        mail.sendPasswordReset("recipient@example.test", "ALOUTE <fan>", "https://aloute.example/reset?token=abc", 30);
        verify(sender).send(message);
        assertThat(message.getSubject()).isEqualTo("Đặt lại mật khẩu ALORA");
        assertThat(message.getContent().toString()).contains("mật khẩu ALORA", "ALOUTE &lt;fan&gt;",
                "https://aloute.example/reset?token=abc");
        assertThat(message.getFrom()[0].toString()).isEqualTo("sender@example.test");
    }

    @Test
    void sanctionUsesAloraAndPreservesUserSuppliedText() throws Exception {
        when(sender.createMimeMessage()).thenReturn(message);
        mail.sendSanctionNotification("recipient@example.test", "ALOUTE <fan>", "WARN", "POST",
                "Bài ALOUTE <script>", "Lý do ALOUTE <test>");
        verify(sender).send(message);
        assertThat(message.getSubject()).startsWith("[ALORA]");
        assertThat(message.getContent().toString()).contains("vi phạm ALORA", "Kiểm duyệt ALORA",
                "ALOUTE &lt;fan&gt;", "ALOUTE &lt;script&gt;", "ALOUTE &lt;test&gt;");
        assertThat(HtmlUtils.htmlUnescape(message.getContent().toString()))
                .contains("Bài ALOUTE <script>", "Lý do ALOUTE <test>");
    }
}
