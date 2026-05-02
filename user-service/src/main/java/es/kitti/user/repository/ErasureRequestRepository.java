package es.kitti.user.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.user.entity.ErasureRequest;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ErasureRequestRepository implements PanacheRepository<ErasureRequest> {

    public Uni<List<ErasureRequest>> findEligibleForPurge() {
        return list("purgedAt IS NULL AND scheduledPurgeAt <= ?1", LocalDateTime.now());
    }
}
