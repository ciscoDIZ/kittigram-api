package es.kitti.adoption.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.adoption.entity.AdoptionRequest;
import es.kitti.adoption.entity.AdoptionStatus;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class AdoptionRequestRepository implements PanacheRepository<AdoptionRequest> {

    public Uni<List<AdoptionRequest>> findByAdopterId(Long adopterId) {
        return list("adopterId", adopterId);
    }

    public Uni<List<AdoptionRequest>> findByOrganizationId(Long organizationId) {
        return list("organizationId", organizationId);
    }

    public Uni<List<AdoptionRequest>> findByCatId(Long catId) {
        return list("catId", catId);
    }

    public Uni<List<AdoptionRequest>> findByCatIdAndOrganizationId(Long catId, Long organizationId) {
        return list("catId = ?1 and organizationId = ?2 order by createdAt desc", catId, organizationId);
    }

    @WithSession
    public Uni<Boolean> existsActiveByCatId(Long catId) {
        return count("catId = ?1 and status not in ?2", catId,
                List.of(AdoptionStatus.Rejected, AdoptionStatus.Completed))
                .onItem().transform(count -> count > 0);
    }

    public Uni<Integer> anonymizeAdopter(Long adopterId) {
        return update("adopterEmail = ?1 where adopterId = ?2",
                adopterId + "@erased.kitties", adopterId);
    }

    public Uni<List<AdoptionRequest>> findRejectedBefore(LocalDateTime cutoff) {
        return list("status = ?1 and updatedAt < ?2", AdoptionStatus.Rejected, cutoff);
    }

    public Uni<List<AdoptionRequest>> findCompletedBefore(LocalDateTime cutoff) {
        return list("status = ?1 and updatedAt < ?2", AdoptionStatus.Completed, cutoff);
    }

    public Uni<Long> deleteByIds(List<Long> ids) {
        if (ids.isEmpty()) return Uni.createFrom().item(0L);
        return delete("id in ?1", ids);
    }
}