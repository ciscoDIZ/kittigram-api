package es.kitti.organization.service;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.organization.dto.*;
import es.kitti.organization.entity.*;
import es.kitti.organization.exception.MemberLimitExceededException;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationMemberRepository;
import es.kitti.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberServiceTest {

    @Mock OrganizationMemberRepository memberRepository;
    @Mock OrganizationRepository organizationRepository;
    @Spy  OrganizationMapper mapper;
    @InjectMocks OrganizationMemberService service;

    private Organization org;
    private OrganizationMember adminMember;
    private OrganizationMember staffMember;

    @BeforeEach
    void setUp() {
        org = new Organization();
        org.id = 1L;
        org.name = "Protectora Test";
        org.status = OrganizationStatus.Active;
        org.plan = OrganizationPlan.Free;
        org.maxMembers = 1;
        org.createdAt = LocalDateTime.now();
        org.updatedAt = LocalDateTime.now();

        adminMember = new OrganizationMember();
        adminMember.id = 1L;
        adminMember.organizationId = 1L;
        adminMember.userId = 10L;
        adminMember.role = MemberRole.Admin;
        adminMember.status = MemberStatus.Active;
        adminMember.joinedAt = LocalDateTime.now();

        staffMember = new OrganizationMember();
        staffMember.id = 2L;
        staffMember.organizationId = 1L;
        staffMember.userId = 20L;
        staffMember.role = MemberRole.Staff;
        staffMember.status = MemberStatus.Active;
        staffMember.joinedAt = LocalDateTime.now();
    }

    @Test
    void testAddCreatorAsAdmin() {
        when(memberRepository.persist(any(OrganizationMember.class)))
                .thenReturn(Uni.createFrom().item(adminMember));

        OrganizationMember saved = service.addCreatorAsAdmin(1L, 10L).await().indefinitely();

        assertEquals(MemberRole.Admin, saved.role);
        verify(memberRepository).persist(any(OrganizationMember.class));
    }

    @Test
    void testInviteMemberExceedsPlanLimit() {
        when(memberRepository.isAdmin(1L, 10L)).thenReturn(Uni.createFrom().item(true));
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().item(org));
        when(memberRepository.countActiveByOrganizationId(1L)).thenReturn(Uni.createFrom().item(1L));

        assertThrows(MemberLimitExceededException.class,
                () -> service.inviteMember(1L, 10L, new InviteMemberRequest(20L, MemberRole.Staff))
                        .await().indefinitely());
    }

    @Test
    void testInviteMemberProPlanUnlimited() {
        org.plan = OrganizationPlan.Pro;
        org.maxMembers = -1;
        when(memberRepository.isAdmin(1L, 10L)).thenReturn(Uni.createFrom().item(true));
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().item(org));
        when(memberRepository.countActiveByOrganizationId(1L)).thenReturn(Uni.createFrom().item(999L));
        when(memberRepository.persist(any(OrganizationMember.class))).thenReturn(Uni.createFrom().item(staffMember));

        MemberResponse response = service.inviteMember(1L, 10L, new InviteMemberRequest(20L, MemberRole.Staff))
                .await().indefinitely();

        assertNotNull(response);
        verify(memberRepository).persist(any(OrganizationMember.class));
    }

    @Test
    void testChangeMemberRoleRequiresAdmin() {
        when(memberRepository.isAdmin(1L, 20L)).thenReturn(Uni.createFrom().item(false));

        assertThrows(ForbiddenException.class,
                () -> service.changeMemberRole(1L, 20L, 20L, new ChangeMemberRoleRequest(MemberRole.Admin))
                        .await().indefinitely());
    }

    @Test
    void testChangeMemberRole() {
        when(memberRepository.isAdmin(1L, 10L)).thenReturn(Uni.createFrom().item(true));
        staffMember.role = MemberRole.Admin;
        when(memberRepository.findActiveByOrganizationIdAndUserId(1L, 20L))
                .thenReturn(Uni.createFrom().item(Optional.of(staffMember)));
        when(memberRepository.persist(any(OrganizationMember.class)))
                .thenReturn(Uni.createFrom().item(staffMember));

        MemberResponse response = service.changeMemberRole(1L, 20L, 10L,
                new ChangeMemberRoleRequest(MemberRole.Admin)).await().indefinitely();

        assertEquals(MemberRole.Admin, response.role());
    }

    @Test
    void testRemoveMemberRequiresAdmin() {
        when(memberRepository.isAdmin(1L, 20L)).thenReturn(Uni.createFrom().item(false));

        assertThrows(ForbiddenException.class,
                () -> service.removeMember(1L, 20L, 20L).await().indefinitely());
    }

    @Test
    void testRemoveMember() {
        when(memberRepository.isAdmin(1L, 10L)).thenReturn(Uni.createFrom().item(true));
        when(memberRepository.findActiveByOrganizationIdAndUserId(1L, 20L))
                .thenReturn(Uni.createFrom().item(Optional.of(staffMember)));
        when(memberRepository.persist(any(OrganizationMember.class)))
                .thenReturn(Uni.createFrom().item(staffMember));

        service.removeMember(1L, 20L, 10L).await().indefinitely();

        assertEquals(MemberStatus.Removed, staffMember.status);
    }

    @Test
    void testListMembersRequiresAdmin() {
        when(memberRepository.isAdmin(1L, 20L)).thenReturn(Uni.createFrom().item(false));

        assertThrows(ForbiddenException.class,
                () -> service.listMembers(1L, 20L).await().indefinitely());
    }

    @Test
    void testListMembers() {
        when(memberRepository.isAdmin(1L, 10L)).thenReturn(Uni.createFrom().item(true));
        when(memberRepository.findActiveByOrganizationId(1L))
                .thenReturn(Uni.createFrom().item(List.of(adminMember, staffMember)));

        List<MemberResponse> members = service.listMembers(1L, 10L).await().indefinitely();

        assertEquals(2, members.size());
    }
}
