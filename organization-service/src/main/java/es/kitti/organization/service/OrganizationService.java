package es.kitti.organization.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.organization.dto.CreateOrganizationRequest;
import es.kitti.organization.dto.OrganizationResponse;
import es.kitti.organization.dto.UpdateOrganizationRequest;
import es.kitti.organization.entity.Organization;
import es.kitti.organization.exception.OrganizationNotFoundException;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationRepository;

@ApplicationScoped
public class OrganizationService {

    @Inject
    OrganizationRepository organizationRepository;

    @Inject
    OrganizationMemberService memberService;

    @Inject
    OrganizationMapper mapper;

    @WithTransaction
    public Uni<OrganizationResponse> create(CreateOrganizationRequest request, Long creatorUserId) {
        Organization org = mapper.toEntity(request);
        return organizationRepository.persist(org)
                .onItem().transformToUni(saved ->
                        memberService.addCreatorAsAdmin(saved.id, creatorUserId)
                                .onItem().transform(m -> mapper.toResponse(saved)));
    }

    @WithSession
    public Uni<OrganizationResponse> findById(Long id, Long callerId) {
        return memberService.requireMember(id, callerId)
                .onItem().transformToUni(ignored -> organizationRepository.findById(id))
                .onItem().ifNull().failWith(() -> new OrganizationNotFoundException(id))
                .onItem().transform(mapper::toResponse);
    }

    @WithSession
    public Uni<OrganizationResponse> findByCurrentUser(Long userId) {
        return memberService.findActiveByUserId(userId)
                .onItem().transformToUni(opt -> {
                    if (opt.isEmpty()) throw new OrganizationNotFoundException(0L);
                    return organizationRepository.findById(opt.get().organizationId);
                })
                .onItem().ifNull().failWith(() -> new OrganizationNotFoundException(0L))
                .onItem().transform(mapper::toResponse);
    }

    @WithTransaction
    public Uni<OrganizationResponse> update(Long id, Long callerId, UpdateOrganizationRequest request) {
        return memberService.requireAdmin(id, callerId)
                .onItem().transformToUni(ignored -> organizationRepository.findById(id))
                .onItem().ifNull().failWith(() -> new OrganizationNotFoundException(id))
                .onItem().transformToUni(org -> {
                    if (request.name() != null)        org.name = request.name();
                    if (request.description() != null) org.description = request.description();
                    if (request.address() != null)     org.address = request.address();
                    if (request.city() != null)        org.city = request.city();
                    if (request.region() != null)      org.region = request.region();
                    if (request.country() != null)     org.country = request.country();
                    if (request.phone() != null)       org.phone = request.phone();
                    if (request.email() != null)       org.email = request.email();
                    if (request.logoUrl() != null)     org.logoUrl = request.logoUrl();
                    return organizationRepository.persist(org);
                })
                .onItem().transform(mapper::toResponse);
    }
}
