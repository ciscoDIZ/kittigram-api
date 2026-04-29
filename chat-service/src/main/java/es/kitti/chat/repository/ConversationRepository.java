package es.kitti.chat.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.chat.entity.Conversation;

import java.util.List;

@ApplicationScoped
public class ConversationRepository implements PanacheRepository<Conversation> {

    public Uni<List<Conversation>> findByUserId(Long userId) {
        return list("userId = ?1 order by coalesce(lastMessageAt, createdAt) desc", userId);
    }

    public Uni<List<Conversation>> findByOrganizationId(Long organizationId) {
        return list("organizationId = ?1 order by coalesce(lastMessageAt, createdAt) desc",
                organizationId);
    }

    public Uni<Conversation> findByIntakeRequestId(Long intakeRequestId) {
        return find("intakeRequestId", intakeRequestId).firstResult();
    }
}