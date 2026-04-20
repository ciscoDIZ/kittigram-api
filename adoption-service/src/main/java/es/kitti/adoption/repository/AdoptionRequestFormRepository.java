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
}