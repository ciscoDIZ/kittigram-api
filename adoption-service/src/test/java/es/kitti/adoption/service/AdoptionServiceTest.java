package es.kitti.adoption.service;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import es.kitti.adoption.client.CatClient;
import es.kitti.adoption.dto.*;
import es.kitti.adoption.entity.*;
import es.kitti.adoption.event.AdoptionFormSubmittedEvent;
import es.kitti.adoption.exception.*;
import es.kitti.adoption.mapper.AdoptionMapper;
import es.kitti.adoption.repository.*;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdoptionServiceTest {

    @Mock
    AdoptionRequestRepository adoptionRequestRepository;

    @Mock
    AdoptionRequestFormRepository adoptionRequestFormRepository;

    @Mock
    AdoptionFormRepository adoptionFormRepository;

    @Mock
    InterviewRepository interviewRepository;

    @Mock
    ExpenseRepository expenseRepository;

    @Mock
    AdoptionMapper adoptionMapper;

    @Mock
    CatClient catClient;

    @Mock
    Emitter<AdoptionFormSubmittedEvent> adoptionFormSubmittedEmitter;

    @InjectMocks
    AdoptionService adoptionService;

    private AdoptionRequest testAdoptionRequest;
    private AdoptionRequestResponse testAdoptionRequestResponse;

    @BeforeEach
    void setUp() {
        testAdoptionRequest = new AdoptionRequest();
        testAdoptionRequest.id = 1L;
        testAdoptionRequest.catId = 10L;
        testAdoptionRequest.adopterId = 100L;
        testAdoptionRequest.organizationId = 200L;
        testAdoptionRequest.status = AdoptionStatus.Pending;

        testAdoptionRequestResponse = new AdoptionRequestResponse(
                1L, 10L, 100L, 200L,
                AdoptionStatus.Pending, null, null,
                "adopter@kitti.es",
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // createAdoptionRequest success and active-adoptions cases use Panache.withTransaction()
    // — those scenarios are covered in AdoptionResourceTest (integration)

    @Test
    void createAdoptionRequest_catDeleted_throwsCatNotAvailableException() {
        var request = new AdoptionRequestCreateRequest(10L, 200L);

        when(catClient.findById(10L))
                .thenReturn(Uni.createFrom().item(Response.status(404).build()));

        assertThrows(CatNotAvailableException.class, () ->
                adoptionService.createAdoptionRequest(request, 100L, "adopter@kitti.es")
                        .await().indefinitely()
        );
    }

    @Test
    void findById_adopter_returnsResponse() {
        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionMapper.toResponse(testAdoptionRequest))
                .thenReturn(testAdoptionRequestResponse);

        var result = adoptionService.findById(1L, 100L)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void findById_organization_returnsResponse() {
        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionMapper.toResponse(testAdoptionRequest))
                .thenReturn(testAdoptionRequestResponse);

        var result = adoptionService.findById(1L, 200L)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void findById_thirdParty_throwsForbiddenException() {
        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));

        assertThrows(ForbiddenException.class, () ->
                adoptionService.findById(1L, 999L)
                        .await().indefinitely()
        );
    }

    @Test
    void findById_notExists_throwsAdoptionRequestNotFoundException() {
        when(adoptionRequestRepository.findById(999L))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(AdoptionRequestNotFoundException.class, () ->
                adoptionService.findById(999L, 100L)
                        .await().indefinitely()
        );
    }

    // updateStatus, submitRequestForm, scheduleInterview, submitAdoptionForm use
    // Panache.withSession/withTransaction (require Vert.x context) — covered in AdoptionResourceTest

    @Test
    void findByAdopterId_returnsListOfResponses() {
        when(adoptionRequestRepository.findByAdopterId(100L))
                .thenReturn(Uni.createFrom().item(List.of(testAdoptionRequest)));
        when(adoptionMapper.toResponse(testAdoptionRequest))
                .thenReturn(testAdoptionRequestResponse);

        var result = adoptionService.findByAdopterId(100L)
                .await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void findByOrganizationId_returnsListOfResponses() {
        when(adoptionRequestRepository.findByOrganizationId(200L))
                .thenReturn(Uni.createFrom().item(List.of(testAdoptionRequest)));
        when(adoptionMapper.toResponse(testAdoptionRequest))
                .thenReturn(testAdoptionRequestResponse);

        var result = adoptionService.findByOrganizationId(200L)
                .await().indefinitely();

        assertEquals(1, result.size());
    }
}