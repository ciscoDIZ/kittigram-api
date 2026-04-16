package org.ciscoadiz.formanalysis.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.ciscoadiz.formanalysis.entity.FormAnalysis;

@ApplicationScoped
public class FormAnalysisRepository implements PanacheRepository<FormAnalysis> {

    public Uni<FormAnalysis> findByAdoptionRequestId(Long adoptionRequestId) {
        return find("adoptionRequestId", adoptionRequestId).firstResult();
    }
}