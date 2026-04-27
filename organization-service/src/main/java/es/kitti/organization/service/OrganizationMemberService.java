package es.kitti.organization.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.organization.dto.ChangeMemberRoleRequest;
import es.kitti.organization.dto.InviteMemberRequest;
import es.kitti.organization.dto.MemberResponse;
import es.kitti.organization.entity.MemberRole;
import es.kitti.organization.entity.MemberStatus;
import es.kitti.organization.entity.OrganizationMember;
import es.kitti.organization.exception.MemberLimitExceededException;
import es.kitti.organization.exception.OrganizationNotFoundException;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationMemberRepository;
import es.kitti.organization.repository.OrganizationRepository;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrganizationMemberService {

    @Inject
    OrganizationMemberRepository memberRepository;

    @Inject
    OrganizationRepository organizationRepository;

    @Inject
    OrganizationMapper mapper;

    public Uni<OrganizationMember> addCreatorAsAdmin(Long organizationId, Long userId) {
        OrganizationMember member = new OrganizationMember();
        member.organizationId = organizationId;
        member.userId = userId;
        member.role = MemberRole.Admin;
        return memberRepository.persist(member);
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

    public Uni<Optional<OrganizationMember>> findActiveByUserId(Long userId) {
        return memberRepository.findActiveByUserId(userId);
    }

    public Uni<Void> requireAdmin(Long organizationId, Long userId) {
        return memberRepository.isAdmin(organizationId, userId)
                .onItem().transformToUni(isAdmin -> {
                    if (!isAdmin) throw new ForbiddenException();
                    return Uni.createFrom().voidItem();
                });
    }

    public Uni<Void> requireMember(Long organizationId, Long userId) {
        return memberRepository.isMember(organizationId, userId)
                .onItem().transformToUni(isMember -> {
                    if (!isMember) throw new ForbiddenException();
                    return Uni.createFrom().voidItem();
                });
    }
}
