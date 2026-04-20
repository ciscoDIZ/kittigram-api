package es.kitti.adoption.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.adoption.entity.Interview;

import java.util.List;

@ApplicationScoped
public class InterviewRepository implements PanacheRepository<Interview> {

    public Uni<List<Interview>> findByAdoptionRequestId(Long adoptionRequestId) {
        return list("adoptionRequestId", adoptionRequestId);
    }
}