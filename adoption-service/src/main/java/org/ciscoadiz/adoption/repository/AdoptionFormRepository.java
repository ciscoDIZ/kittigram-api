package org.ciscoadiz.adoption.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.ciscoadiz.adoption.entity.AdoptionForm;

@ApplicationScoped
public class AdoptionFormRepository implements PanacheRepository<AdoptionForm> {

    public Uni<AdoptionForm> findByAdoptionRequestId(Long adoptionRequestId) {
        return find("adoptionRequestId", adoptionRequestId).firstResult();
    }
}