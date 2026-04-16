package org.ciscoadiz.adoption.service;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
import org.ciscoadiz.adoption.dto.*;
import org.ciscoadiz.adoption.entity.*;
import org.ciscoadiz.adoption.event.AdoptionFormSubmittedEvent;
import org.ciscoadiz.adoption.exception.*;
import org.ciscoadiz.adoption.mapper.AdoptionMapper;
import org.ciscoadiz.adoption.repository.*;
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
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void createAdoptionRequest_catAvailable_success() {
        var request = new AdoptionRequestCreateRequest(10L, 200L);

        when(adoptionRequestRepository.existsActiveByCatId(10L))
                .thenReturn(Uni.createFrom().item(false));
        when(adoptionMapper.toEntity(request, 100L))
                .thenReturn(testAdoptionRequest);
        when(adoptionRequestRepository.persist(any(AdoptionRequest.class)))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionMapper.toResponse(testAdoptionRequest))
                .thenReturn(testAdoptionRequestResponse);

        var result = adoptionService.createAdoptionRequest(request, 100L)
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
                adoptionService.createAdoptionRequest(request, 100L)
                        .await().indefinitely()
        );
    }

    @Test
    void findById_exists_returnsResponse() {
        when(adoptionRequestRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testAdoptionRequest));
        when(adoptionMapper.toResponse(testAdoptionRequest))
                .thenReturn(testAdoptionRequestResponse);

        var result = adoptionService.findById(1L)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void findById_notExists_throwsAdoptionRequestNotFoundException() {
        when(adoptionRequestRepository.findById(999L))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(AdoptionRequestNotFoundException.class, () ->
                adoptionService.findById(999L)
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
}