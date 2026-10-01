package com.aloute.chat;

import com.aloute.common.RateLimitExceededException;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 3c: gửi và liệt kê tin nhắn (chữ, ảnh/video/file đính kèm). Tạo hội thoại ở {@link ChatServiceIT}. */
class MessageServiceIT extends IntegrationTest {

    @Autowired ChatService chats;
    @Autowired MessageService messages;

    private static byte[] jpeg(int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] mp4(int size) {
        byte[] data = new byte[size];
        byte[] head = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(head, 0, data, 0, head.length);
        return data;
    }

    @Test
    void sendsAPlainTextMessage() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        MessageView view = messages.send(a.getId(), direct.getId(), "  Chào cậu!  ", null);

        assertThat(view.contentHtml()).isEqualTo("Chào cậu!");
        assertThat(view.sender().id()).isEqualTo(a.getId());
        assertThat(view.attachment()).isNull();
    }

    @Test
    void sendsAnImageAttachment() throws IOException {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());
        var image = new MockMultipartFile("attachment", "cat.jpg", "image/jpeg", jpeg(80, 60));

        MessageView view = messages.send(a.getId(), direct.getId(), null, image);

        assertThat(view.attachment().kind()).isEqualTo(AttachmentKind.IMAGE);
        assertThat(view.attachment().url()).startsWith("/uploads/chat/").endsWith(".jpg");
        assertThat(view.attachment().originalName()).isEqualTo("cat.jpg");
    }

    @Test
    void sendsAVideoAttachment() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());
        var video = new MockMultipartFile("attachment", "clip.mp4", "video/mp4", mp4(1000));

        MessageView view = messages.send(a.getId(), direct.getId(), null, video);

        assertThat(view.attachment().kind()).isEqualTo(AttachmentKind.VIDEO);
    }

    @Test
    void sendsAGenericFileAttachment() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());
        var file = new MockMultipartFile("attachment", "bao-cao.pdf", "application/pdf",
                "not really a pdf but that's fine".getBytes(StandardCharsets.UTF_8));

        MessageView view = messages.send(a.getId(), direct.getId(), "Gửi cậu file này", file);

        assertThat(view.attachment().kind()).isEqualTo(AttachmentKind.FILE);
        assertThat(view.attachment().originalName()).isEqualTo("bao-cao.pdf");
        assertThat(view.attachment().url()).endsWith(".pdf");
    }

    @Test
    void rejectsAnEmptyMessage() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        assertThatThrownBy(() -> messages.send(a.getId(), direct.getId(), "   ", null))
                .isInstanceOf(ChatActionException.class);
    }

    @Test
    void rejectsMessagesLongerThanTheLimit() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        assertThatThrownBy(() -> messages.send(a.getId(), direct.getId(), "a".repeat(Message.MAX_CONTENT_LENGTH + 1), null))
                .isInstanceOf(ChatActionException.class);
    }

    @Test
    void nonMembersCannotSendOrReadMessages() {
        User a = createUser();
        User b = createUser();
        User stranger = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        assertThatThrownBy(() -> messages.send(stranger.getId(), direct.getId(), "xin chào", null))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> messages.history(stranger.getId(), direct.getId(), null))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void historyReturnsMessagesInChronologicalOrder() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());
        messages.send(a.getId(), direct.getId(), "một", null);
        clock.advance(Duration.ofSeconds(1));
        messages.send(b.getId(), direct.getId(), "hai", null);
        clock.advance(Duration.ofSeconds(1));
        messages.send(a.getId(), direct.getId(), "ba", null);

        MessagePage page = messages.history(a.getId(), direct.getId(), null);

        assertThat(page.messages()).extracting(MessageView::contentHtml).containsExactly("một", "hai", "ba");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void historyPagesBackwardsWithoutGapsOrDuplicates() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());
        int total = MessageService.PAGE_SIZE + 5;
        for (int i = 1; i <= total; i++) {
            messages.send(i % 2 == 0 ? a.getId() : b.getId(), direct.getId(), "tin " + i, null);
            clock.advance(Duration.ofSeconds(1));
        }

        MessagePage latest = messages.history(a.getId(), direct.getId(), null);
        assertThat(latest.messages()).hasSize(MessageService.PAGE_SIZE);
        assertThat(latest.messages().get(latest.messages().size() - 1).contentHtml()).isEqualTo("tin " + total);
        assertThat(latest.hasMore()).isTrue();

        MessagePage older = messages.history(a.getId(), direct.getId(), latest.nextCursor());
        assertThat(older.messages()).extracting(MessageView::contentHtml)
                .containsExactly("tin 1", "tin 2", "tin 3", "tin 4", "tin 5");
        assertThat(older.hasMore()).isFalse();
    }

    @Test
    void historyTreatsABrokenCursorAsTheLatestPage() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());
        messages.send(a.getId(), direct.getId(), "một", null);

        assertThat(messages.history(a.getId(), direct.getId(), "không-phải-con-trỏ").messages()).hasSize(1);
    }

    @Test
    void sendingUpdatesTheConversationPreviewAndUnreadCountForOthers() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        clock.advance(Duration.ofSeconds(1));
        messages.send(a.getId(), direct.getId(), "này cậu", null);

        var forB = chats.listFor(b.getId());
        assertThat(forB).hasSize(1);
        assertThat(forB.get(0).lastMessagePreview()).isEqualTo("này cậu");
        assertThat(forB.get(0).unread()).isEqualTo(1);
        assertThat(chats.totalUnread(b.getId())).isEqualTo(1);

        assertThat(chats.totalUnread(a.getId())).as("người gửi không tự tính tin của mình là chưa đọc").isZero();
    }

    @Test
    void markingReadClearsTheUnreadCountForNewerReadsOnly() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());
        clock.advance(Duration.ofSeconds(1));
        messages.send(a.getId(), direct.getId(), "1", null);

        clock.advance(Duration.ofSeconds(1));
        chats.markRead(b.getId(), direct.getId());
        assertThat(chats.totalUnread(b.getId())).isZero();

        clock.advance(Duration.ofSeconds(1));
        messages.send(a.getId(), direct.getId(), "2", null);
        assertThat(chats.totalUnread(b.getId())).isEqualTo(1);
    }

    @Test
    void moreThanSixtyMessagesInAMinuteIsRejected() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());
        for (int i = 0; i < 60; i++) {
            messages.send(a.getId(), direct.getId(), "tin " + i, null);
        }

        assertThatThrownBy(() -> messages.send(a.getId(), direct.getId(), "quá tay", null))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
