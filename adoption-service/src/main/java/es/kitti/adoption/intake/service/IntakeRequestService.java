package es.kitti.adoption.intake.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.adoption.intake.dto.IntakeDecisionRequest;
import es.kitti.adoption.intake.dto.IntakeRequestCreateRequest;
import es.kitti.adoption.intake.dto.IntakeRequestResponse;
import es.kitti.adoption.intake.entity.IntakeRequest;
import es.kitti.adoption.intake.entity.IntakeStatus;
import es.kitti.adoption.intake.exception.IntakeRequestNotFoundException;
import es.kitti.adoption.intake.exception.InvalidIntakeStatusException;
import es.kitti.adoption.intake.mapper.IntakeMapper;
import es.kitti.adoption.intake.repository.IntakeRequestRepository;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class IntakeRequestService {

    @Inject
    IntakeRequestRepository repository;

    @Inject
    IntakeMapper mapper;

    @WithTransaction
    public Uni<IntakeRequestResponse> create(IntakeRequestCreateRequest request, Long userId) {
        IntakeRequest entity = mapper.toEntity(request, userId);
        return repository.persist(entity)
                .onItem().transform(mapper::toResponse);
    }

    @WithSession
    public Uni<List<IntakeRequestResponse>> findMine(Long userId) {
        return repository.findByUserId(userId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithSession
    public Uni<List<IntakeRequestResponse>> findByOrganization(Long organizationId) {
        return repository.findByTargetOrganizationId(organizationId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithTransaction
    public Uni<IntakeRequestResponse> approve(Long id, Long callerOrgId) {
        return findPendingForOrg(id, callerOrgId)
                .onItem().transformToUni(entity -> {
                    entity.status = IntakeStatus.Approved;
                    entity.decidedAt = LocalDateTime.now();
                    return repository.persist(entity);
                })
                .onItem().transform(mapper::toResponse);
    }

    @WithTransaction
    public Uni<IntakeRequestResponse> reject(Long id, IntakeDecisionRequest decision, Long callerOrgId) {
        return findPendingForOrg(id, callerOrgId)
                .onItem().transformToUni(entity -> {
                    entity.status = IntakeStatus.Rejected;
                    entity.rejectionReason = decision.reason();
                    entity.decidedAt = LocalDateTime.now();
                    return repository.persist(entity);
                })
                .onItem().transform(mapper::toResponse);
    }

    private Uni<IntakeRequest> findPendingForOrg(Long id, Long callerOrgId) {
        return repository.findById(id)
                .onItem().ifNull().failWith(() -> new IntakeRequestNotFoundException(id))
                .onItem().invoke(entity -> {
                    requireSameOrganization(entity.targetOrganizationId, callerOrgId);
                    if (entity.status != IntakeStatus.Pending) {
                        throw new InvalidIntakeStatusException(entity.status, IntakeStatus.Pending);
                    }
                });
    }

    private void requireSameOrganization(Long expected, Long callerOrgId) {
        if (!expected.equals(callerOrgId)) {
            throw new ForbiddenException();
        }
    }
}