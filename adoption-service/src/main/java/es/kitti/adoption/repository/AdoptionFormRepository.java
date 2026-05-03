package es.kitti.adoption.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.adoption.entity.AdoptionForm;

@ApplicationScoped
public class AdoptionFormRepository implements PanacheRepository<AdoptionForm> {

    public Uni<AdoptionForm> findByAdoptionRequestId(Long adoptionRequestId) {
        return find("adoptionRequestId", adoptionRequestId).firstResult();
    }

    public Uni<Integer> anonymizeForRequestIds(java.util.List<Long> requestIds, String encryptedPlaceholder) {
        if (requestIds.isEmpty()) return io.smallrye.mutiny.Uni.createFrom().item(0);
        return update(
                "fullName = 'SUPRIMIDO', idNumber = ?1, phone = '000000000', " +
                "address = 'SUPRIMIDO', city = 'SUPRIMIDO', postalCode = '00000', " +
                "additionalNotes = null where adoptionRequestId in ?2",
                encryptedPlaceholder, requestIds);
    }

    public Uni<Long> deleteByRequestIds(java.util.List<Long> requestIds) {
        if (requestIds.isEmpty()) return io.smallrye.mutiny.Uni.createFrom().item(0L);
        return delete("adoptionRequestId in ?1", requestIds);
    }
}