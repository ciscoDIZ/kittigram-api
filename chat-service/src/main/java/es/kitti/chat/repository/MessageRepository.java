package es.kitti.chat.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.chat.entity.Message;

import java.util.List;

@ApplicationScoped
public class MessageRepository implements PanacheRepository<Message> {

    public Uni<List<Message>> findByConversationId(Long conversationId) {
        return list("conversationId = ?1 order by createdAt asc", conversationId);
    }

    public Uni<Integer> anonymizeSender(Long userId) {
        return update("senderId = 0L where senderId = ?1 and senderType = ?2",
                userId, es.kitti.chat.entity.SenderType.User);
    }

    public Uni<Long> deleteByConversationIds(List<Long> conversationIds) {
        if (conversationIds.isEmpty()) return Uni.createFrom().item(0L);
        return delete("conversationId in ?1", conversationIds);
    }
}