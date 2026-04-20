package es.kitti.adoption.service;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void createAdoptionRequest_catAvailable_success() {
        var request = new AdoptionRequestCreateRequest(10L, 200L);

        when(adoptionRequestRepository.existsActiveByCatId(10L))
                .thenReturn(Uni.createFrom().item(false));
        when(adoptionMapper.toEntity(request, 100L, "adopter@kitti.es"))
                .thenReturn(testAdoptionRequest);
        when(adoptionRequestRepository.persist(any(AdoptionRequest.class)))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionMapper.toResponse(testAdoptionRequest))
                .thenReturn(testAdoptionRequestResponse);

        var result = adoptionService.createAdoptionRequest(request, 100L, "adopter@kitti.es")
                .await().indefinitely();

        assertNotNull(result);
        assertEquals(AdoptionStatus.Pending, result.status());
        assertEquals(10L, result.catId());
    }

    @Test
    void createAdoptionRequest_catNotAvailable_throwsCatNotAvailableException() {
        var request = new AdoptionRequestCreateRequest(10L, 200L);

        when(adoptionRequestRepository.existsActiveByCatId(10L))
                .thenReturn(Uni.createFrom().item(true));

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

    @Test
    void updateStatus_organizationOwner_success() {
        var request = new AdoptionStatusUpdateRequest(AdoptionStatus.Accepted, null);

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionRequestRepository.<AdoptionRequest>persist(any(AdoptionRequest.class)))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionMapper.toResponse(testAdoptionRequest))
                .thenReturn(testAdoptionRequestResponse);

        assertDoesNotThrow(() ->
                adoptionService.updateStatus(1L, request, 200L)
                        .await().indefinitely()
        );
    }

    @Test
    void updateStatus_notOrganizationOwner_throwsForbiddenException() {
        var request = new AdoptionStatusUpdateRequest(AdoptionStatus.Accepted, null);

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));

        assertThrows(ForbiddenException.class, () ->
                adoptionService.updateStatus(1L, request, 999L)
                        .await().indefinitely()
        );
    }

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
    void submitAdoptionForm_alreadySubmitted_throwsAdoptionFormAlreadySubmittedException() {
        testAdoptionRequest.status = AdoptionStatus.Accepted;
        var existingForm = new AdoptionForm();

        var request = new AdoptionFormCreateRequest(
                "Test User", "12345678A", "666666666",
                "Calle Test 1", "La Orotava", "38300",
                true, true, true, true, null
        );

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionFormRepository.findByAdoptionRequestId(1L))
                .thenReturn(Uni.createFrom().item(existingForm));

        assertThrows(AdoptionFormAlreadySubmittedException.class, () ->
                adoptionService.submitAdoptionForm(1L, request, 100L)
                        .await().indefinitely()
        );
    }

    @Test
    void scheduleInterview_wrongStatus_throwsInvalidAdoptionStatusException() {
        var request = new InterviewCreateRequest(LocalDateTime.now().plusDays(7), "Notes");

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));

        assertThrows(InvalidAdoptionStatusException.class, () ->
                adoptionService.scheduleInterview(1L, request, 200L)
                        .await().indefinitely()
        );
    }

    @Test
    void submitRequestForm_validStatus_success() {
        var form = new AdoptionRequestForm();
        form.id = 1L;
        form.adoptionRequestId = 1L;

        var formResponse = new AdoptionRequestFormResponse(
                1L, 1L, true, null, 2, false, null,
                false, null, 8, true, null,
                HousingType.Apartment, 60, false, false, null,
                true, true, true, ActivityLevel.Quiet,
                "instinct", 30, "toys", "ignore", true, true,
                "love cats", true, true, true, false, null,
                LocalDateTime.now()
        );

        var request = new AdoptionRequestFormCreateRequest(
                true, null, 2, false, null, false, null,
                8, true, null, HousingType.Apartment, 60,
                false, false, null, true, true, true,
                ActivityLevel.Quiet, "instinct", 30, "toys",
                "ignore", true, true, "love cats", true,
                true, true, false, null
        );

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionRequestFormRepository.persist(any(AdoptionRequestForm.class)))
                .thenReturn(Uni.createFrom().item(form));
        when(adoptionRequestRepository.<AdoptionRequest>persist(any(AdoptionRequest.class)))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionMapper.toEntity(any(AdoptionRequestFormCreateRequest.class), eq(1L)))
                .thenReturn(form);
        when(adoptionMapper.toResponse(form))
                .thenReturn(formResponse);

        var result = adoptionService.submitRequestForm(1L, request, 100L)
                .await().indefinitely();

        assertNotNull(result);
        verify(adoptionFormSubmittedEmitter).send(any(AdoptionFormSubmittedEvent.class));
    }

    @Test
    void submitRequestForm_wrongStatus_throwsInvalidAdoptionStatusException() {
        testAdoptionRequest.status = AdoptionStatus.Reviewing;

        var request = new AdoptionRequestFormCreateRequest(
                true, null, 2, false, null, false, null,
                8, true, null, HousingType.Apartment, 60,
                false, false, null, true, true, true,
                ActivityLevel.Quiet, "instinct", 30, "toys",
                "ignore", true, true, "love cats", true,
                true, true, false, null
        );

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));

        assertThrows(InvalidAdoptionStatusException.class, () ->
                adoptionService.submitRequestForm(1L, request, 100L)
                        .await().indefinitely()
        );
    }

    @Test
    void submitRequestForm_notAdopter_throwsForbiddenException() {
        var request = new AdoptionRequestFormCreateRequest(
                true, null, 2, false, null, false, null,
                8, true, null, HousingType.Apartment, 60,
                false, false, null, true, true, true,
                ActivityLevel.Quiet, "instinct", 30, "toys",
                "ignore", true, true, "love cats", true,
                true, true, false, null
        );

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));

        assertThrows(ForbiddenException.class, () ->
                adoptionService.submitRequestForm(1L, request, 999L)
                        .await().indefinitely()
        );
    }

    @Test
    void scheduleInterview_validStatus_success() {
        testAdoptionRequest.status = AdoptionStatus.Accepted;

        var interview = new Interview();
        interview.id = 1L;
        interview.adoptionRequestId = 1L;
        interview.scheduledAt = LocalDateTime.now().plusDays(7);

        var interviewResponse = new InterviewResponse(
                1L, 1L, interview.scheduledAt, "Notes", null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        var request = new InterviewCreateRequest(
                LocalDateTime.now().plusDays(7), "Notes"
        );

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionMapper.toEntity(any(InterviewCreateRequest.class), eq(1L)))
                .thenReturn(interview);
        when(interviewRepository.persist(any(Interview.class)))
                .thenReturn(Uni.createFrom().item(interview));
        when(adoptionMapper.toResponse(interview))
                .thenReturn(interviewResponse);

        var result = adoptionService.scheduleInterview(1L, request, 200L)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals(1L, result.adoptionRequestId());
    }

    @Test
    void submitAdoptionForm_notAdopter_throwsForbiddenException() {
        testAdoptionRequest.status = AdoptionStatus.Accepted;

        var request = new AdoptionFormCreateRequest(
                "Test User", "12345678A", "666666666",
                "Calle Test 1", "La Orotava", "38300",
                true, true, true, true, null
        );

        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));

        assertThrows(ForbiddenException.class, () ->
                adoptionService.submitAdoptionForm(1L, request, 999L)
                        .await().indefinitely()
        );
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