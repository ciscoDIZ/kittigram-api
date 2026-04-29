package es.kitti.chat.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ChatService {

    @Inject
    ConversationRepository conversationRepository;

    @Inject
    MessageRepository messageRepository;

    @Inject
    ChatMapper mapper;

    @WithTransaction
    public Uni<ConversationResponse> createConversation(CreateConversationRequest request) {
        return conversationRepository.findByIntakeRequestId(request.intakeRequestId())
                .onItem().invoke(existing -> {
                    if (existing != null) {
                        throw new ConversationAlreadyExistsException(request.intakeRequestId());
                    }
                })
                .onItem().transformToUni(ignored -> {
                    Conversation c = new Conversation();
                    c.intakeRequestId = request.intakeRequestId();
                    c.userId = request.userId();
                    c.organizationId = request.organizationId();
                    return conversationRepository.persist(c);
                })
                .onItem().transform(mapper::toResponse);
    }

    @WithSession
    public Uni<List<ConversationResponse>> findMineAsUser(Long userId) {
        return conversationRepository.findByUserId(userId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithSession
    public Uni<List<ConversationResponse>> findMineAsOrganization(Long organizationId) {
        return conversationRepository.findByOrganizationId(organizationId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithSession
    public Uni<List<MessageResponse>> listMessages(Long conversationId, Long callerId, SenderType callerType) {
        return loadAndAuthorize(conversationId, callerId, callerType)
                .onItem().transformToUni(c -> messageRepository.findByConversationId(conversationId))
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithTransaction
    public Uni<MessageResponse> sendMessage(Long conversationId, SendMessageRequest request,
                                            Long callerId, SenderType callerType) {
        return loadAndAuthorize(conversationId, callerId, callerType)
                .onItem().transformToUni(c -> {
                    Message m = new Message();
                    m.conversationId = c.id;
                    m.senderId = callerId;
                    m.senderType = callerType;
                    m.content = request.content();
                    return messageRepository.<Message>persist(m)
                            .onItem().call(saved -> {
                                c.lastMessageAt = LocalDateTime.now();
                                return conversationRepository.persist(c);
                            });
                })
                .onItem().transform(mapper::toResponse);
    }

    private Uni<Conversation> loadAndAuthorize(Long conversationId, Long callerId, SenderType callerType) {
        return conversationRepository.findById(conversationId)
                .onItem().ifNull().failWith(() -> new ConversationNotFoundException(conversationId))
                .onItem().invoke(c -> requireParticipant(c, callerId, callerType));
    }

    private void requireParticipant(Conversation c, Long callerId, SenderType callerType) {
        boolean ok = switch (callerType) {
            case User -> c.userId.equals(callerId);
            case Organization -> c.organizationId.equals(callerId);
        };
        if (!ok) throw new ForbiddenException();
    }
}