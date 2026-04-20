package es.kitti.organization.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.organization.dto.*;
import es.kitti.organization.entity.*;
import es.kitti.organization.exception.MemberLimitExceededException;
import es.kitti.organization.exception.OrganizationNotFoundException;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationMemberRepository;
import es.kitti.organization.repository.OrganizationRepository;

import java.util.List;

@ApplicationScoped
public class OrganizationService {

    @Inject
    OrganizationRepository organizationRepository;

    @Inject
    OrganizationMemberRepository memberRepository;

    @Inject
    OrganizationMapper mapper;

    @WithTransaction
    public Uni<OrganizationResponse> create(CreateOrganizationRequest request, Long creatorUserId) {
        Organization org = mapper.toEntity(request);
        return organizationRepository.persist(org)
                .onItem().transformToUni(saved -> {
                    OrganizationMember member = new OrganizationMember();
                    member.organizationId = saved.id;
                    member.userId = creatorUserId;
                    member.role = MemberRole.Admin;
                    return memberRepository.persist(member)
                            .onItem().transform(m -> mapper.toResponse(saved));
                });
    }

    @WithSession
    public Uni<OrganizationResponse> findById(Long id) {
        return organizationRepository.findById(id)
                .onItem().ifNull().failWith(() -> new OrganizationNotFoundException(id))
                .onItem().transform(mapper::toResponse);
    }

    @WithSession
    public Uni<OrganizationResponse> findByCurrentUser(Long userId) {
        return memberRepository.findActiveByUserId(userId)
                .onItem().transformToUni(opt -> {
                    if (opt.isEmpty()) throw new OrganizationNotFoundException(0L);
                    return organizationRepository.findById(opt.get().organizationId);
                })
                .onItem().ifNull().failWith(() -> new OrganizationNotFoundException(0L))
                .onItem().transform(mapper::toResponse);
    }

    @WithTransaction
    public Uni<OrganizationResponse> update(Long id, Long callerId, UpdateOrganizationRequest request) {
        return requireAdmin(id, callerId)
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

    @WithSession
    public Uni<List<MemberResponse>> listMembers(Long organizationId, Long callerId) {
        return requireAdmin(organizationId, callerId)
                .onItem().transformToUni(ignored ->
                        memberRepository.findActiveByOrganizationId(organizationId))
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithTransaction
    public Uni<MemberResponse> inviteMember(Long organizationId, Long callerId, InviteMemberRequest request) {
        return requireAdmin(organizationId, callerId)
                .onItem().transformToUni(ignored -> organizationRepository.findById(organizationId))
                .onItem().ifNull().failWith(() -> new OrganizationNotFoundException(organizationId))
                .onItem().transformToUni(org ->
                        memberRepository.countActiveByOrganizationId(organizationId)
                                .onItem().transformToUni(count -> {
                                    if (org.maxMembers != -1 && count >= org.maxMembers) {
                                        throw new MemberLimitExceededException(org.maxMembers);
                                    }
                                    OrganizationMember member = new OrganizationMember();
                                    member.organizationId = organizationId;
                                    member.userId = request.userId();
                                    member.role = request.role();
                                    return memberRepository.persist(member);
                                }))
                .onItem().transform(mapper::toResponse);
    }

    @WithTransaction
    public Uni<MemberResponse> changeMemberRole(Long organizationId, Long targetUserId, Long callerId, ChangeMemberRoleRequest request) {
        return requireAdmin(organizationId, callerId)
                .onItem().transformToUni(ignored ->
                        memberRepository.findActiveByOrganizationIdAndUserId(organizationId, targetUserId))
                .onItem().transformToUni(opt -> {
                    if (opt.isEmpty()) throw new ForbiddenException();
                    OrganizationMember member = opt.get();
                    member.role = request.role();
                    return memberRepository.persist(member);
                })
                .onItem().transform(mapper::toResponse);
    }

    @WithTransaction
    public Uni<Void> removeMember(Long organizationId, Long targetUserId, Long callerId) {
        return requireAdmin(organizationId, callerId)
                .onItem().transformToUni(ignored ->
                        memberRepository.findActiveByOrganizationIdAndUserId(organizationId, targetUserId))
                .onItem().transformToUni(opt -> {
                    if (opt.isEmpty()) throw new ForbiddenException();
                    OrganizationMember member = opt.get();
                    member.status = MemberStatus.Removed;
                    return memberRepository.persist(member);
                })
                .onItem().transform(m -> null);
    }

    private Uni<Void> requireAdmin(Long organizationId, Long userId) {
        return memberRepository.isAdmin(organizationId, userId)
                .onItem().transformToUni(isAdmin -> {
                    if (!isAdmin) throw new ForbiddenException();
                    return Uni.createFrom().voidItem();
                });
    }
}
