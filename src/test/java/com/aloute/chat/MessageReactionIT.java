package com.aloute.chat;

import com.aloute.dto.chat.MessageView;
import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.dto.reaction.Reactor;
import com.aloute.exception.chat.ConversationNotFoundException;
import com.aloute.model.chat.Conversation;
import com.aloute.model.reaction.ReactionType;
import com.aloute.model.user.User;
import com.aloute.service.chat.ChatService;
import com.aloute.service.chat.MessageReactionService;
import com.aloute.service.chat.MessageService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageReactionIT extends IntegrationTest {

    @Autowired MessageReactionService messageReactions;
    @Autowired MessageService messages;
    @Autowired ChatService chats;

    @Test
    void reactingTwiceWithTheSameTypeUnreacts() {
        User a = createUser();
        User b = createUser();
        Conversation conv = chats.startDirect(a.getId(), b.getId());
        MessageView msg = messages.send(a.getId(), conv.getId(), "Xin chào!", null);

        ReactionSummary first = messageReactions.toggle(b.getId(), msg.id(), ReactionType.FIRE);
        assertThat(first.mine()).isEqualTo(ReactionType.FIRE);
        assertThat(first.count(ReactionType.FIRE)).isEqualTo(1);
        assertThat(first.total()).isEqualTo(1);

        ReactionSummary second = messageReactions.toggle(b.getId(), msg.id(), ReactionType.FIRE);
        assertThat(second.mine()).isNull();
        assertThat(second.count(ReactionType.FIRE)).isEqualTo(0);
        assertThat(second.total()).isEqualTo(0);
    }

    @Test
    void reactingWithADifferentTypeReplacesTheFirstOne() {
        User a = createUser();
        User b = createUser();
        Conversation conv = chats.startDirect(a.getId(), b.getId());
        MessageView msg = messages.send(a.getId(), conv.getId(), "Xin chào!", null);

        messageReactions.toggle(b.getId(), msg.id(), ReactionType.FIRE);
        ReactionSummary result = messageReactions.toggle(b.getId(), msg.id(), ReactionType.WOW);

        assertThat(result.mine()).isEqualTo(ReactionType.WOW);
        assertThat(result.count(ReactionType.FIRE)).isEqualTo(0);
        assertThat(result.count(ReactionType.WOW)).isEqualTo(1);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void nonMemberCannotReactToMessage() {
        User a = createUser();
        User b = createUser();
        User stranger = createUser();
        Conversation conv = chats.startDirect(a.getId(), b.getId());
        MessageView msg = messages.send(a.getId(), conv.getId(), "Bí mật!", null);

        assertThatThrownBy(() -> messageReactions.toggle(stranger.getId(), msg.id(), ReactionType.LOVE))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void reactorsReturnsListOfReactorsForMessage() {
        User a = createUser();
        User b = createUser();
        Conversation conv = chats.startDirect(a.getId(), b.getId());
        MessageView msg = messages.send(a.getId(), conv.getId(), "Xin chào!", null);

        messageReactions.toggle(b.getId(), msg.id(), ReactionType.LOVE);

        List<Reactor> reactors = messageReactions.reactors(msg.id(), a.getId());
        assertThat(reactors).hasSize(1);
        assertThat(reactors.get(0).user().id()).isEqualTo(b.getId());
        assertThat(reactors.get(0).type()).isEqualTo(ReactionType.LOVE);
    }
}
