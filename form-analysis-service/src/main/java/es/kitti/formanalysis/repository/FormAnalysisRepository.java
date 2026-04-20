package es.kitti.formanalysis.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.formanalysis.entity.FormAnalysis;

@ApplicationScoped
public class FormAnalysisRepository implements PanacheRepository<FormAnalysis> {

    public Uni<FormAnalysis> findByAdoptionRequestId(Long adoptionRequestId) {
        return find("adoptionRequestId", adoptionRequestId).firstResult();
    }
}