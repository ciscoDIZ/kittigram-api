package es.kitti.chat.mapper;

import es.kitti.chat.dto.ConversationResponse;
import es.kitti.chat.dto.MessageResponse;
import es.kitti.chat.entity.Conversation;
import es.kitti.chat.entity.Message;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChatMapper {

    public ConversationResponse toResponse(Conversation c) {
        return new ConversationResponse(
                c.id, c.intakeRequestId, c.userId, c.organizationId,
                c.createdAt, c.lastMessageAt
        );
    }

    public MessageResponse toResponse(Message m) {
        return new MessageResponse(
                m.id, m.conversationId, m.senderId, m.senderType, m.content, m.createdAt
        );
    }
}