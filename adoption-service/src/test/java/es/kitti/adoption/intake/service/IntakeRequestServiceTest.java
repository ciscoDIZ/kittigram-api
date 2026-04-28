package es.kitti.adoption.intake.service;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.adoption.intake.client.OrganizationClient;
import es.kitti.adoption.intake.client.OrganizationPublicMinimal;
import es.kitti.adoption.intake.dto.IntakeDecisionRequest;
import es.kitti.adoption.intake.dto.IntakeRequestCreateRequest;
import es.kitti.adoption.intake.dto.IntakeRequestResponse;
import es.kitti.adoption.intake.entity.IntakeRequest;
import es.kitti.adoption.intake.entity.IntakeStatus;
import es.kitti.adoption.intake.exception.IntakeRequestNotFoundException;
import es.kitti.adoption.intake.exception.InvalidIntakeStatusException;
import es.kitti.adoption.intake.mapper.IntakeMapper;
import es.kitti.adoption.intake.repository.IntakeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntakeRequestServiceTest {

    @Mock
    IntakeRequestRepository repository;

    @Mock
    IntakeMapper mapper;

    @Mock
    OrganizationClient organizationClient;

    @InjectMocks
    IntakeRequestService service;

    private IntakeRequest pending;
    private IntakeRequestResponse pendingResponse;

    @BeforeEach
    void setUp() {
        service.internalSecret = "test-secret";

        pending = new IntakeRequest();
        pending.id = 1L;
        pending.userId = 100L;
        pending.targetOrganizationId = 200L;
        pending.catName = "Mishi";
        pending.catAge = 3;
        pending.region = "Santa Cruz de Tenerife";
        pending.city = "La Orotava";
        pending.vaccinated = true;
        pending.description = "Friendly cat";
        pending.status = IntakeStatus.Pending;
        pending.createdAt = LocalDateTime.now();

        pendingResponse = new IntakeRequestResponse(
                1L, 100L, 200L, "Mishi", 3,
                "Santa Cruz de Tenerife", "La Orotava", true,
                "Friendly cat", IntakeStatus.Pending, null,
                pending.createdAt, null
        );
    }

    @Test
    void create_success() {
        var request = new IntakeRequestCreateRequest(
                200L, "Mishi", 3,
                "Santa Cruz de Tenerife", "La Orotava", true, "Friendly cat"
        );

        when(mapper.toEntity(request, 100L)).thenReturn(pending);
        when(repository.persist(any(IntakeRequest.class)))
                .thenReturn(Uni.createFrom().item(pending));
        when(mapper.toResponse(pending)).thenReturn(pendingResponse);

        var result = service.create(request, 100L).await().indefinitely();

        assertNotNull(result);
        assertEquals(IntakeStatus.Pending, result.status());
        assertEquals(100L, result.userId());
    }

    @Test
    void findMine_returnsList() {
        when(repository.findByUserId(100L))
                .thenReturn(Uni.createFrom().item(List.of(pending)));
        when(mapper.toResponse(pending)).thenReturn(pendingResponse);

        var result = service.findMine(100L).await().indefinitely();

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).userId());
    }

    @Test
    void findByOrganization_returnsList() {
        when(repository.findByTargetOrganizationId(200L))
                .thenReturn(Uni.createFrom().item(List.of(pending)));
        when(mapper.toResponse(pending)).thenReturn(pendingResponse);

        var result = service.findByOrganization(200L).await().indefinitely();

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).targetOrganizationId());
    }

    @Test
    void approve_pendingAndOwner_success() {
        when(repository.findById(1L))
                .thenReturn(Uni.createFrom().item(pending));
        when(repository.<IntakeRequest>persist(any(IntakeRequest.class)))
                .thenReturn(Uni.createFrom().item(pending));
        when(mapper.toResponse(any(IntakeRequest.class)))
                .thenReturn(pendingResponse);

        assertDoesNotThrow(() ->
                service.approve(1L, 200L).await().indefinitely()
        );
        assertEquals(IntakeStatus.Approved, pending.status);
        assertNotNull(pending.decidedAt);
    }

    @Test
    void approve_notOwner_throwsForbidden() {
        when(repository.findById(1L))
                .thenReturn(Uni.createFrom().item(pending));

        assertThrows(ForbiddenException.class, () ->
                service.approve(1L, 999L).await().indefinitely()
        );
    }

    @Test
    void approve_notFound_throwsNotFound() {
        when(repository.findById(999L))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(IntakeRequestNotFoundException.class, () ->
                service.approve(999L, 200L).await().indefinitely()
        );
    }

    @Test
    void approve_notPending_throwsInvalidStatus() {
        pending.status = IntakeStatus.Approved;
        when(repository.findById(1L))
                .thenReturn(Uni.createFrom().item(pending));

        assertThrows(InvalidIntakeStatusException.class, () ->
                service.approve(1L, 200L).await().indefinitely()
        );
    }

    @Test
    void reject_pendingAndOwner_returnsRejectionWithFilteredAlternatives() {
        var decision = new IntakeDecisionRequest("Out of capacity");
        var rejectingOrg = new OrganizationPublicMinimal(
                200L, "Rejecting Org", "La Orotava", "Santa Cruz de Tenerife", "+34", "r@kitti.es");
        var alt = new OrganizationPublicMinimal(
                201L, "Other Org", "La Laguna", "Santa Cruz de Tenerife", "+34", "o@kitti.es");

        when(repository.findById(1L))
                .thenReturn(Uni.createFrom().item(pending));
        when(repository.<IntakeRequest>persist(any(IntakeRequest.class)))
                .thenReturn(Uni.createFrom().item(pending));
        when(mapper.toResponse(any(IntakeRequest.class)))
                .thenReturn(pendingResponse);
        when(organizationClient.findByRegion(eq("Santa Cruz de Tenerife"), eq("test-secret")))
                .thenReturn(Uni.createFrom().item(List.of(rejectingOrg, alt)));

        var result = service.reject(1L, decision, 200L).await().indefinitely();

        assertEquals(IntakeStatus.Rejected, pending.status);
        assertEquals("Out of capacity", pending.rejectionReason);
        assertNotNull(pending.decidedAt);
        assertEquals(1, result.alternatives().size(), "rejecting org must be excluded");
        assertEquals(201L, result.alternatives().get(0).id());
        assertNotNull(result.intake());
    }

    @Test
    void reject_clientFails_returnsRejectionWithEmptyAlternatives() {
        var decision = new IntakeDecisionRequest("nope");

        when(repository.findById(1L))
                .thenReturn(Uni.createFrom().item(pending));
        when(repository.<IntakeRequest>persist(any(IntakeRequest.class)))
                .thenReturn(Uni.createFrom().item(pending));
        when(mapper.toResponse(any(IntakeRequest.class)))
                .thenReturn(pendingResponse);
        when(organizationClient.findByRegion(any(), any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("organization-service down")));

        var result = service.reject(1L, decision, 200L).await().indefinitely();

        assertEquals(IntakeStatus.Rejected, pending.status);
        assertTrue(result.alternatives().isEmpty(),
                "client failure must not break the rejection itself");
    }

    @Test
    void reject_notOwner_throwsForbidden() {
        var decision = new IntakeDecisionRequest("nope");
        when(repository.findById(1L))
                .thenReturn(Uni.createFrom().item(pending));

        assertThrows(ForbiddenException.class, () ->
                service.reject(1L, decision, 999L).await().indefinitely()
        );
    }

    @Test
    void reject_notPending_throwsInvalidStatus() {
        pending.status = IntakeStatus.Rejected;
        var decision = new IntakeDecisionRequest("already done");
        when(repository.findById(1L))
                .thenReturn(Uni.createFrom().item(pending));

        assertThrows(InvalidIntakeStatusException.class, () ->
                service.reject(1L, decision, 200L).await().indefinitely()
        );
    }
}
