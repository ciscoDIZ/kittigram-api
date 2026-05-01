package es.kitti.adoption.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.adoption.client.CatClient;
import es.kitti.adoption.dto.*;
import es.kitti.adoption.entity.*;
import es.kitti.adoption.event.AdoptionFormAnalysedEvent;
import es.kitti.adoption.event.AdoptionFormSubmittedEvent;
import es.kitti.adoption.exception.*;
import es.kitti.adoption.mapper.AdoptionMapper;
import es.kitti.adoption.repository.*;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class AdoptionService {

    @Inject
    AdoptionRequestRepository adoptionRequestRepository;

    @Inject
    AdoptionRequestFormRepository adoptionRequestFormRepository;

    @Inject
    AdoptionFormRepository adoptionFormRepository;

    @Inject
    InterviewRepository interviewRepository;

    @Inject
    ExpenseRepository expenseRepository;

    @Inject
    AdoptionMapper adoptionMapper;

    @RestClient
    CatClient catClient;

    @Inject
    @Channel("adoption-form-submitted")
    Emitter<AdoptionFormSubmittedEvent> adoptionFormSubmittedEmitter;

    public Uni<AdoptionRequestResponse> createAdoptionRequest(
            AdoptionRequestCreateRequest request, Long adopterId, String adopterEmail) {

        return catClient.findById(request.catId())
                .onFailure(jakarta.ws.rs.WebApplicationException.class)
                .recoverWithItem(e -> ((jakarta.ws.rs.WebApplicationException) e).getResponse())
                .onItem().transformToUni(response -> {
                    if (response.getStatus() != 200) {
                        return Uni.createFrom().failure(new CatNotAvailableException(request.catId()));
                    }
                    return Panache.withTransaction(() ->
                            adoptionRequestRepository.existsActiveByCatId(request.catId())
                                    .onItem().transformToUni(exists -> {
                                        if (exists) {
                                            return Uni.createFrom()
                                                    .failure(new CatNotAvailableException(request.catId()));
                                        }
                                        AdoptionRequest entity = adoptionMapper.toEntity(request, adopterId, adopterEmail);
                                        return adoptionRequestRepository.persist(entity);
                                    })
                    );
                })
                .onItem().transform(adoptionMapper::toResponse);
    }

    @WithSession
    public Uni<AdoptionRequestResponse> findById(Long id, Long callerId) {
        return adoptionRequestRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> new AdoptionRequestNotFoundException(id))
                .onItem().invoke(adoption -> requireParticipant(adoption, callerId))
                .onItem().transform(adoptionMapper::toResponse);
    }

    @WithSession
    public Uni<List<AdoptionRequestResponse>> findByAdopterId(Long adopterId) {
        return adoptionRequestRepository.findByAdopterId(adopterId)
                .onItem().transform(list -> list.stream()
                        .map(adoptionMapper::toResponse)
                        .toList());
    }

    @WithSession
    public Uni<List<AdoptionRequestResponse>> findByOrganizationId(Long organizationId) {
        return adoptionRequestRepository.findByOrganizationId(organizationId)
                .onItem().transform(list -> list.stream()
                        .map(adoptionMapper::toResponse)
                        .toList());
    }

    public Uni<AdoptionRequestResponse> updateStatus(
            Long id, AdoptionStatusUpdateRequest request, Long userId) {

        boolean isTerminal = request.status() == AdoptionStatus.Rejected
                || request.status() == AdoptionStatus.Completed;

        return Panache.withSession(() ->
                adoptionRequestRepository.findById(id)
                        .onItem().ifNull().failWith(() -> new AdoptionRequestNotFoundException(id))
                        .onItem().invoke(adoption -> requireOrganizationOwner(adoption, userId))
                        .onItem().transform(adoption -> adoption.catId)
        )
        .onItem().transformToUni(catId -> isTerminal ? Uni.createFrom().voidItem() : verifyCatActive(catId))
        .onItem().transformToUni(__ -> Panache.withTransaction(() ->
                adoptionRequestRepository.findById(id)
                        .onItem().ifNull().failWith(() -> new AdoptionRequestNotFoundException(id))
                        .onItem().transformToUni(adoption -> {
                            requireOrganizationOwner(adoption, userId);
                            adoption.status = request.status();
                            adoption.rejectionReason = request.reason();
                            return adoptionRequestRepository.persist(adoption);
                        })
        ))
        .onItem().transform(adoptionMapper::toResponse);
    }

    public Uni<AdoptionRequestFormResponse> submitRequestForm(
            Long adoptionRequestId, AdoptionRequestFormCreateRequest request, Long adopterId) {

        return Panache.withSession(() ->
                adoptionRequestRepository.findById(adoptionRequestId)
                        .onItem().ifNull().failWith(() -> new AdoptionRequestNotFoundException(adoptionRequestId))
                        .onItem().invoke(adoption -> {
                            requireAdopter(adoption, adopterId);
                            requireStatus(adoption, AdoptionStatus.Pending);
                        })
                        .onItem().transform(adoption -> adoption.catId)
        )
        .onItem().transformToUni(this::verifyCatActive)
        .onItem().transformToUni(__ -> Panache.withTransaction(() ->
                adoptionRequestRepository.findById(adoptionRequestId)
                        .onItem().ifNull().failWith(() -> new AdoptionRequestNotFoundException(adoptionRequestId))
                        .onItem().transformToUni(adoption -> {
                            requireAdopter(adoption, adopterId);
                            requireStatus(adoption, AdoptionStatus.Pending);
                            AdoptionRequestForm form = adoptionMapper.toEntity(request, adoptionRequestId);
                            return adoptionRequestFormRepository.persist(form)
                                    .onItem().invoke(saved -> {
                                        adoption.status = AdoptionStatus.Reviewing;
                                        adoptionRequestRepository.persist(adoption).subscribe().with(
                                                v -> {},
                                                e -> {}
                                        );
                                        adoptionFormSubmittedEmitter.send(
                                                buildFormSubmittedEvent(adoption, saved)
                                        );
                                    });
                        })
        ))
        .onItem().transform(adoptionMapper::toResponse);
    }

    public Uni<InterviewResponse> scheduleInterview(
            Long adoptionRequestId, InterviewCreateRequest request, Long organizationId) {

        return Panache.withSession(() ->
                adoptionRequestRepository.findById(adoptionRequestId)
                        .onItem().ifNull().failWith(() -> new AdoptionRequestNotFoundException(adoptionRequestId))
                        .onItem().invoke(adoption -> {
                            requireOrganizationOwner(adoption, organizationId);
                            requireStatus(adoption, AdoptionStatus.Accepted);
                        })
                        .onItem().transform(adoption -> adoption.catId)
        )
        .onItem().transformToUni(this::verifyCatActive)
        .onItem().transformToUni(__ -> Panache.withTransaction(() ->
                adoptionRequestRepository.findById(adoptionRequestId)
                        .onItem().ifNull().failWith(() -> new AdoptionRequestNotFoundException(adoptionRequestId))
                        .onItem().transformToUni(adoption -> {
                            requireOrganizationOwner(adoption, organizationId);
                            requireStatus(adoption, AdoptionStatus.Accepted);
                            Interview interview = adoptionMapper.toEntity(request, adoptionRequestId);
                            return interviewRepository.persist(interview);
                        })
        ))
        .onItem().transform(adoptionMapper::toResponse);
    }

    public Uni<AdoptionFormResponse> submitAdoptionForm(
            Long adoptionRequestId, AdoptionFormCreateRequest request, Long adopterId) {

        return Panache.withSession(() ->
                adoptionRequestRepository.findById(adoptionRequestId)
                        .onItem().ifNull().failWith(() -> new AdoptionRequestNotFoundException(adoptionRequestId))
                        .onItem().invoke(adoption -> {
                            requireAdopter(adoption, adopterId);
                            requireStatus(adoption, AdoptionStatus.Accepted);
                        })
                        .onItem().transform(adoption -> adoption.catId)
        )
        .onItem().transformToUni(this::verifyCatActive)
        .onItem().transformToUni(__ -> Panache.withTransaction(() ->
                adoptionRequestRepository.findById(adoptionRequestId)
                        .onItem().ifNull().failWith(() -> new AdoptionRequestNotFoundException(adoptionRequestId))
                        .onItem().transformToUni(adoption -> {
                            requireAdopter(adoption, adopterId);
                            requireStatus(adoption, AdoptionStatus.Accepted);
                            return adoptionFormRepository.findByAdoptionRequestId(adoptionRequestId)
                                    .onItem().transformToUni(existing -> {
                                        if (existing != null) {
                                            return Uni.createFrom().failure(
                                                    new AdoptionFormAlreadySubmittedException(adoptionRequestId)
                                            );
                                        }
                                        AdoptionForm form = adoptionMapper.toEntity(request, adoptionRequestId);
                                        return adoptionFormRepository.persist(form)
                                                .onItem().invoke(saved -> {
                                                    adoption.status = AdoptionStatus.FormCompleted;
                                                    adoptionRequestRepository.persist(adoption)
                                                            .subscribe().with(v -> {}, e -> {});
                                                });
                                    });
                        })
        ))
        .onItem().transform(adoptionMapper::toResponse);
    }

    @Incoming("adoption-form-analysed")
    public Uni<Void> onFormAnalysed(String message) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            AdoptionFormAnalysedEvent event = mapper.readValue(
                    message, AdoptionFormAnalysedEvent.class);

            return Panache.withTransaction(() -> adoptionRequestRepository.findById(event.adoptionRequestId())
                    .onItem().ifNull()
                    .failWith(() -> new AdoptionRequestNotFoundException(event.adoptionRequestId()))
                    .onItem().transformToUni(adoption -> {
                        if ("REJECTED".equals(event.decision())) {
                            adoption.status = AdoptionStatus.Rejected;
                            adoption.rejectionReason = event.rejectionReason();
                        }
                        return adoptionRequestRepository.persist(adoption).replaceWithVoid();
                    }));
        } catch (Exception e) {
            return Uni.createFrom().voidItem();
        }
    }

    private AdoptionFormSubmittedEvent buildFormSubmittedEvent(
            AdoptionRequest adoption, AdoptionRequestForm form) {
        return new AdoptionFormSubmittedEvent(
                adoption.adopterEmail,
                adoption.id,
                adoption.catId,
                adoption.adopterId,
                adoption.organizationId,
                form.hasPreviousCatExperience,
                form.previousPetsHistory,
                form.adultsInHousehold,
                form.hasChildren,
                form.childrenAges,
                form.hasOtherPets,
                form.otherPetsDescription,
                form.hoursAlonePerDay,
                form.stableHousing,
                form.housingInstabilityReason,
                form.housingType != null ? form.housingType.name() : null,
                form.housingSize,
                form.hasOutdoorAccess,
                form.isRental,
                form.rentalPetsAllowed,
                form.hasWindowsWithView,
                form.hasVerticalSpace,
                form.hasHidingSpots,
                form.householdActivityLevel != null ? form.householdActivityLevel.name() : null,
                form.whyCatsNeedToPlay,
                form.dailyPlayMinutes,
                form.plannedEnrichment,
                form.reactionToUnwantedBehavior,
                form.hasScratchingPost,
                form.willingToEnrichEnvironment,
                form.motivationToAdopt,
                form.understandsLongTermCommitment,
                form.hasVetBudget,
                form.allHouseholdMembersAgree,
                form.anyoneHasAllergies,
                form.allergiesDetail
        );
    }

    private Uni<Void> verifyCatActive(Long catId) {
        return catClient.findById(catId)
                .onFailure(jakarta.ws.rs.WebApplicationException.class)
                .recoverWithItem(e -> ((jakarta.ws.rs.WebApplicationException) e).getResponse())
                .onItem().transformToUni(response -> {
                    if (response.getStatus() != 200) {
                        return Uni.createFrom().failure(new CatNotAvailableException(catId));
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    private void requireParticipant(AdoptionRequest adoption, Long callerId) {
        if (!adoption.adopterId.equals(callerId) && !adoption.organizationId.equals(callerId)) {
            throw new ForbiddenException("Access denied");
        }
    }

    private void requireAdopter(AdoptionRequest adoption, Long adopterId) {
        if (!adoption.adopterId.equals(adopterId)) {
            throw new ForbiddenException("Access denied");
        }
    }

    private void requireOrganizationOwner(AdoptionRequest adoption, Long organizationId) {
        if (!adoption.organizationId.equals(organizationId)) {
            throw new ForbiddenException("Access denied");
        }
    }

    private void requireStatus(AdoptionRequest adoption, AdoptionStatus required) {
        if (adoption.status != required) {
            throw new InvalidAdoptionStatusException(adoption.status, required);
        }
    }
}