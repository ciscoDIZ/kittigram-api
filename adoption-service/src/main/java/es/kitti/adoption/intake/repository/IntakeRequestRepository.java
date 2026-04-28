package es.kitti.adoption.intake.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.adoption.intake.entity.IntakeRequest;
import es.kitti.adoption.intake.entity.IntakeStatus;

import java.util.List;

@ApplicationScoped
public class IntakeRequestRepository implements PanacheRepository<IntakeRequest> {

    public Uni<List<IntakeRequest>> findByUserId(Long userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    public Uni<List<IntakeRequest>> findByTargetOrganizationId(Long organizationId) {
        return list("targetOrganizationId = ?1 order by createdAt desc", organizationId);
    }

    public Uni<List<IntakeRequest>> findByTargetOrganizationIdAndStatus(Long organizationId, IntakeStatus status) {
        return list("targetOrganizationId = ?1 and status = ?2 order by createdAt desc",
                organizationId, status);
    }
}
