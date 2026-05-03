package es.kitti.adoption.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.adoption.entity.AdoptionRequestForm;

@ApplicationScoped
public class AdoptionRequestFormRepository implements PanacheRepository<AdoptionRequestForm> {

    public Uni<AdoptionRequestForm> findByAdoptionRequestId(Long adoptionRequestId) {
        return find("adoptionRequestId", adoptionRequestId).firstResult();
    }

    public Uni<Integer> clearAllergiesForRequestIds(java.util.List<Long> requestIds) {
        if (requestIds.isEmpty()) return io.smallrye.mutiny.Uni.createFrom().item(0);
        return update("allergiesDetail = null where adoptionRequestId in ?1", requestIds);
    }

    public Uni<Long> deleteByRequestIds(java.util.List<Long> requestIds) {
        if (requestIds.isEmpty()) return io.smallrye.mutiny.Uni.createFrom().item(0L);
        return delete("adoptionRequestId in ?1", requestIds);
    }
}