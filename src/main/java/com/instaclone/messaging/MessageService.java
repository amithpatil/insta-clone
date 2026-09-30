package com.instaclone.messaging;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One send path for both transports: the REST endpoint (offline-delivery fallback) and the STOMP
 * destination (live delivery) both call sendMessage — persistence and participant checks live
 * here exactly once. After persisting, every other participant gets a live push regardless of
 * which transport the sender used.
 */
@Service
public class MessageService {

    private static final int MAX_CONVERSATIONS = 50;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public ConversationResponse getOrCreateConversation(Long creatorId, CreateConversationRequest request) {
        User creator = userRepository.findById(creatorId).orElseThrow(() -> new NotFoundException("User not found"));

        List<User> others = request.participantUsernames().stream()
                .map(username -> userRepository
                        .findByUsername(username)
                        .orElseThrow(() -> new NotFoundException("User not found: " + username)))
                .filter(u -> !u.getId().equals(creatorId))
                .distinct()
                .toList();
        if (others.isEmpty()) {
            throw new BadRequestException("A conversation needs at least one other participant");
        }

        Conversation conversation;
        if (others.size() == 1) {
            conversation = conversationRepository
                    .findOneToOneConversation(creatorId, others.get(0).getId())
                    .orElseGet(() -> createConversation(creator, others, false));
        } else {
            conversation = createConversation(creator, others, true);
        }
        return toResponse(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(Long userId) {
        return conversationRepository.findByParticipantIdOrderByLastActivity(userId, MAX_CONVERSATIONS).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MessageResponse sendMessage(Long conversationId, Long senderId, SendMessageRequest request) {
        if (blank(request.content()) && blank(request.mediaUrl())) {
            throw new BadRequestException("A message needs content or a mediaUrl");
        }
        Conversation conversation =
                conversationRepository.findById(conversationId).orElseThrow(() -> new NotFoundException("Conversation not found"));
        assertParticipant(conversationId, senderId);
        User sender = userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found"));

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.content());
        message.setMediaUrl(request.mediaUrl());
        message.setCreatedAt(Instant.now());
        message = messageRepository.save(message);

        MessageResponse response = toResponse(message, UserSummary.from(sender));
        for (User participant : conversation.getParticipants()) {
            if (!participant.getId().equals(senderId)) {
                messagingTemplate.convertAndSendToUser(String.valueOf(participant.getId()), "/queue/messages", response);
            }
        }
        return response;
    }

    @Transactional(readOnly = true)
    public CursorPage<MessageResponse> getHistory(Long conversationId, Long viewerId, String cursor, int limit) {
        assertParticipant(conversationId, viewerId);

        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Message> rows = decoded == null
                ? messageRepository.findFirstPageByConversationId(conversationId, limit + 1)
                : messageRepository.findPageByConversationIdAfterCursor(
                        conversationId, decoded.createdAt(), decoded.id(), limit + 1);

        CursorPage<Message> page = CursorPage.of(rows, limit, m -> new Cursor(m.getCreatedAt(), m.getId()).encode());

        Set<Long> senderIds = page.items().stream().map(m -> m.getSender().getId()).collect(Collectors.toSet());
        Map<Long, UserSummary> sendersById = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));

        List<MessageResponse> items = page.items().stream()
                .map(m -> toResponse(m, sendersById.get(m.getSender().getId())))
                .toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    private Conversation createConversation(User creator, List<User> others, boolean group) {
        Conversation conversation = new Conversation();
        conversation.setGroup(group);
        conversation.setCreatedAt(Instant.now());
        Set<User> participants = new HashSet<>(others);
        participants.add(creator);
        conversation.setParticipants(participants);
        return conversationRepository.save(conversation);
    }

    private void assertParticipant(Long conversationId, Long userId) {
        if (!conversationRepository.existsByIdAndParticipantsId(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant in this conversation");
        }
    }

    private ConversationResponse toResponse(Conversation conversation) {
        List<UserSummary> participants = conversation.getParticipants().stream()
                .map(UserSummary::from)
                .sorted(Comparator.comparing(UserSummary::username))
                .toList();
        return new ConversationResponse(conversation.getId(), conversation.isGroup(), participants, conversation.getCreatedAt());
    }

    private MessageResponse toResponse(Message message, UserSummary sender) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                sender,
                message.getContent(),
                message.getMediaUrl(),
                message.getCreatedAt());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
