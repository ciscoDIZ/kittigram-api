package es.kitti.chat.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.chat.entity.BlockedParticipant;

@ApplicationScoped
public class BlockedParticipantRepository implements PanacheRepository<BlockedParticipant> {

    public Uni<BlockedParticipant> findByOrgAndUser(Long organizationId, Long userId) {
        return find("organizationId = ?1 and userId = ?2", organizationId, userId).firstResult();
    }

    public Uni<Boolean> existsByOrgAndUser(Long organizationId, Long userId) {
        return count("organizationId = ?1 and userId = ?2", organizationId, userId)
                .onItem().transform(c -> c > 0);
    }
}
