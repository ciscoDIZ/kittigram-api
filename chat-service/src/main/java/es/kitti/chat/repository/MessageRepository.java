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
}