package com.aloute.service.chat;

import com.aloute.dto.chat.AuthorViews;
import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.dto.reaction.Reactor;
import com.aloute.exception.chat.ChatActionException;
import com.aloute.model.chat.Message;
import com.aloute.model.chat.MessageReaction;
import com.aloute.model.common.RateAction;
import com.aloute.model.reaction.ReactionType;
import com.aloute.repository.chat.MessageReactionRepository;
import com.aloute.repository.chat.MessageRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.common.RateLimiter;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thả/đổi/bỏ cảm xúc cho tin nhắn chat.
 */
@Service
public class MessageReactionService {

    public static final int MAX_REACTORS = 200;

    private final MessageReactionRepository reactions;
    private final MessageRepository messages;
    private final ChatService chats;
    private final UserRepository users;
    private final RateLimiter rateLimiter;

    public MessageReactionService(MessageReactionRepository reactions, MessageRepository messages,
                                  ChatService chats, UserRepository users,
                                  RateLimiter rateLimiter) {
        this.reactions = reactions;
        this.messages = messages;
        this.chats = chats;
        this.users = users;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public ReactionSummary toggle(UUID userId, UUID messageId, ReactionType type) {
        rateLimiter.check(RateAction.REACTION, userId);
        Message message = messages.findById(messageId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy tin nhắn."));
        chats.requireMembership(userId, message.getConversation().getId());

        Optional<MessageReaction> existing = reactions.findByMessageIdAndUserId(messageId, userId);
        if (existing.isPresent() && existing.get().getType() == type) {
            reactions.delete(existing.get());
        } else {
            MessageReaction reaction = existing.orElseGet(MessageReaction::new);
            if (reaction.getId() == null) {
                reaction.setMessage(message);
                reaction.setUser(users.getReferenceById(userId));
            }
            reaction.setType(type);
            reactions.save(reaction);
        }
        return summarize(messageId, userId);
    }

    @Transactional(readOnly = true)
    public List<Reactor> reactors(UUID messageId, UUID viewerId) {
        Message message = messages.findById(messageId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy tin nhắn."));
        if (viewerId != null) {
            chats.requireMembership(viewerId, message.getConversation().getId());
        }
        return reactions.findReactors(messageId, PageRequest.of(0, MAX_REACTORS)).stream()
                .map(r -> new Reactor(AuthorViews.of(r.getUser()), r.getType())).toList();
    }

    @Transactional(readOnly = true)
    public ReactionSummary summarize(UUID messageId, UUID viewerId) {
        Map<ReactionType, Long> counts = ReactionSummary.emptyCounts();
        for (MessageReactionRepository.MessageReactionCount row : reactions.countsByMessageIds(List.of(messageId))) {
            counts.put(row.getType(), row.getTotal());
        }
        ReactionType mine = viewerId == null ? null
                : reactions.findByMessageIdAndUserId(messageId, viewerId).map(MessageReaction::getType).orElse(null);
        return new ReactionSummary(counts, mine);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ReactionSummary> summarizeAll(Collection<UUID> messageIds, UUID viewerId) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<ReactionType, Long>> countsByMessage = new HashMap<>();
        for (MessageReactionRepository.MessageReactionCount row : reactions.countsByMessageIds(messageIds)) {
            countsByMessage.computeIfAbsent(row.getMessageId(), id -> ReactionSummary.emptyCounts()).put(row.getType(), row.getTotal());
        }
        Map<UUID, ReactionType> mineByMessage = new HashMap<>();
        if (viewerId != null) {
            for (MessageReactionRepository.MyMessageReaction row : reactions.myReactions(messageIds, viewerId)) {
                mineByMessage.put(row.getMessageId(), row.getType());
            }
        }
        Map<UUID, ReactionSummary> result = new HashMap<>();
        for (UUID messageId : messageIds) {
            result.put(messageId, new ReactionSummary(
                    countsByMessage.getOrDefault(messageId, ReactionSummary.emptyCounts()), mineByMessage.get(messageId)));
        }
        return result;
    }
}
