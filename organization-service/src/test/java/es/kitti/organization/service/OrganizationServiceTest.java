package es.kitti.organization.service;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.organization.dto.*;
import es.kitti.organization.entity.*;
import es.kitti.organization.exception.OrganizationNotFoundException;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationMemberService memberService;
    @Spy  OrganizationMapper mapper;
    @InjectMocks OrganizationService service;

    private Organization org;
    private OrganizationMember adminMember;

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
    }

    @Test
    void testCreateOrganization() {
        when(organizationRepository.persist(any(Organization.class)))
                .thenReturn(Uni.createFrom().item(org));
        when(memberService.addCreatorAsAdmin(1L, 10L))
                .thenReturn(Uni.createFrom().item(adminMember));

        OrganizationResponse response = service.create(
                new CreateOrganizationRequest("Protectora Test", null, null, null, null, null, null, null, null),
                10L
        ).await().indefinitely();

        assertEquals("Protectora Test", response.name());
    }

    @Test
    void testFindByIdNotFound() {
        when(memberService.requireMember(99L, 10L)).thenReturn(Uni.createFrom().voidItem());
        when(organizationRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        assertThrows(OrganizationNotFoundException.class,
                () -> service.findById(99L, 10L).await().indefinitely());
    }

    @Test
    void testFindByIdForbiddenForNonMember() {
        when(memberService.requireMember(1L, 999L))
                .thenReturn(Uni.createFrom().failure(new ForbiddenException()));

        assertThrows(ForbiddenException.class,
                () -> service.findById(1L, 999L).await().indefinitely());
    }

    @Test
    void testFindByCurrentUser() {
        when(memberService.findActiveByUserId(10L))
                .thenReturn(Uni.createFrom().item(Optional.of(adminMember)));
        when(organizationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(org));

        OrganizationResponse response = service.findByCurrentUser(10L).await().indefinitely();
        assertEquals(1L, response.id());
    }

    @Test
    void testFindByCurrentUserNotMember() {
        when(memberService.findActiveByUserId(99L))
                .thenReturn(Uni.createFrom().item(Optional.empty()));

        assertThrows(OrganizationNotFoundException.class,
                () -> service.findByCurrentUser(99L).await().indefinitely());
    }

    @Test
    void testUpdateRequiresAdmin() {
        when(memberService.requireAdmin(1L, 20L))
                .thenReturn(Uni.createFrom().failure(new ForbiddenException()));

        assertThrows(ForbiddenException.class,
                () -> service.update(1L, 20L, new UpdateOrganizationRequest("New Name", null, null, null, null, null, null, null, null))
                        .await().indefinitely());
    }

    @Test
    void testUpdateByAdmin() {
        when(memberService.requireAdmin(1L, 10L)).thenReturn(Uni.createFrom().voidItem());
        org.name = "Updated";
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().item(org));
        when(organizationRepository.persist(any(Organization.class))).thenReturn(Uni.createFrom().item(org));

        OrganizationResponse response = service.update(1L, 10L,
                new UpdateOrganizationRequest("Updated", null, null, null, null, null, null, null, null))
                .await().indefinitely();

        assertEquals("Updated", response.name());
    }
}
