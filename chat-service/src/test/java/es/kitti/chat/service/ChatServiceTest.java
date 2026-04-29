package es.kitti.chat.service;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.chat.dto.ConversationResponse;
import es.kitti.chat.dto.CreateConversationRequest;
import es.kitti.chat.dto.MessageResponse;
import es.kitti.chat.dto.SendMessageRequest;
import es.kitti.chat.entity.Conversation;
import es.kitti.chat.entity.Message;
import es.kitti.chat.entity.SenderType;
import es.kitti.chat.exception.ConversationAlreadyExistsException;
import es.kitti.chat.exception.ConversationNotFoundException;
import es.kitti.chat.mapper.ChatMapper;
import es.kitti.chat.repository.ConversationRepository;
import es.kitti.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository messageRepository;
    @Mock ChatMapper mapper;

    @InjectMocks ChatService service;

    private Conversation conversation;
    private ConversationResponse conversationResponse;

    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        conversation.id = 1L;
        conversation.intakeRequestId = 10L;
        conversation.userId = 100L;
        conversation.organizationId = 200L;
        conversation.createdAt = LocalDateTime.now();

        conversationResponse = new ConversationResponse(
                1L, 10L, 100L, 200L, conversation.createdAt, null
        );
    }

    @Test
    void createConversation_success() {
        var request = new CreateConversationRequest(10L, 100L, 200L);

        when(conversationRepository.findByIntakeRequestId(10L))
                .thenReturn(Uni.createFrom().nullItem());
        when(conversationRepository.persist(any(Conversation.class)))
                .thenReturn(Uni.createFrom().item(conversation));
        when(mapper.toResponse(any(Conversation.class)))
                .thenReturn(conversationResponse);

        var result = service.createConversation(request).await().indefinitely();

        assertNotNull(result);
        assertEquals(10L, result.intakeRequestId());
    }

    @Test
    void createConversation_alreadyExists_throws() {
        var request = new CreateConversationRequest(10L, 100L, 200L);

        when(conversationRepository.findByIntakeRequestId(10L))
                .thenReturn(Uni.createFrom().item(conversation));

        assertThrows(ConversationAlreadyExistsException.class, () ->
                service.createConversation(request).await().indefinitely()
        );
    }

    @Test
    void findMineAsUser_returnsList() {
        when(conversationRepository.findByUserId(100L))
                .thenReturn(Uni.createFrom().item(List.of(conversation)));
        when(mapper.toResponse(conversation)).thenReturn(conversationResponse);

        var result = service.findMineAsUser(100L).await().indefinitely();

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).userId());
    }

    @Test
    void findMineAsOrganization_returnsList() {
        when(conversationRepository.findByOrganizationId(200L))
                .thenReturn(Uni.createFrom().item(List.of(conversation)));
        when(mapper.toResponse(conversation)).thenReturn(conversationResponse);

        var result = service.findMineAsOrganization(200L).await().indefinitely();

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).organizationId());
    }

    @Test
    void listMessages_asParticipant_success() {
        var msg = new Message();
        msg.id = 1L; msg.conversationId = 1L; msg.senderId = 100L;
        msg.senderType = SenderType.User; msg.content = "hi"; msg.createdAt = LocalDateTime.now();
        var msgResponse = new MessageResponse(1L, 1L, 100L, SenderType.User, "hi", msg.createdAt);

        when(conversationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(conversation));
        when(messageRepository.findByConversationId(1L))
                .thenReturn(Uni.createFrom().item(List.of(msg)));
        when(mapper.toResponse(msg)).thenReturn(msgResponse);

        var result = service.listMessages(1L, 100L, SenderType.User).await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void listMessages_notParticipant_throwsForbidden() {
        when(conversationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(conversation));

        assertThrows(ForbiddenException.class, () ->
                service.listMessages(1L, 999L, SenderType.User).await().indefinitely()
        );
    }

    @Test
    void listMessages_notFound_throws() {
        when(conversationRepository.findById(99L))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(ConversationNotFoundException.class, () ->
                service.listMessages(99L, 100L, SenderType.User).await().indefinitely()
        );
    }

    @Test
    void sendMessage_asUser_success() {
        var request = new SendMessageRequest("hello");
        var saved = new Message();
        saved.id = 5L; saved.conversationId = 1L; saved.senderId = 100L;
        saved.senderType = SenderType.User; saved.content = "hello";
        saved.createdAt = LocalDateTime.now();
        var msgResponse = new MessageResponse(5L, 1L, 100L, SenderType.User, "hello", saved.createdAt);

        when(conversationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(conversation));
        when(messageRepository.<Message>persist(any(Message.class)))
                .thenReturn(Uni.createFrom().item(saved));
        when(conversationRepository.<Conversation>persist(any(Conversation.class)))
                .thenReturn(Uni.createFrom().item(conversation));
        when(mapper.toResponse(any(Message.class))).thenReturn(msgResponse);

        var result = service.sendMessage(1L, request, 100L, SenderType.User).await().indefinitely();

        assertNotNull(result);
        assertEquals("hello", result.content());
        assertEquals(SenderType.User, result.senderType());
        assertNotNull(conversation.lastMessageAt, "lastMessageAt must be bumped on send");
    }

    @Test
    void sendMessage_asOrganization_success() {
        var request = new SendMessageRequest("from org");
        var saved = new Message();
        saved.id = 6L; saved.conversationId = 1L; saved.senderId = 200L;
        saved.senderType = SenderType.Organization; saved.content = "from org";
        saved.createdAt = LocalDateTime.now();
        var msgResponse = new MessageResponse(6L, 1L, 200L, SenderType.Organization, "from org", saved.createdAt);

        when(conversationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(conversation));
        when(messageRepository.<Message>persist(any(Message.class)))
                .thenReturn(Uni.createFrom().item(saved));
        when(conversationRepository.<Conversation>persist(any(Conversation.class)))
                .thenReturn(Uni.createFrom().item(conversation));
        when(mapper.toResponse(any(Message.class))).thenReturn(msgResponse);

        var result = service.sendMessage(1L, request, 200L, SenderType.Organization)
                .await().indefinitely();

        assertEquals(SenderType.Organization, result.senderType());
    }

    @Test
    void sendMessage_notParticipant_throwsForbidden() {
        var request = new SendMessageRequest("nope");
        when(conversationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(conversation));

        assertThrows(ForbiddenException.class, () ->
                service.sendMessage(1L, request, 999L, SenderType.User).await().indefinitely()
        );
    }
}