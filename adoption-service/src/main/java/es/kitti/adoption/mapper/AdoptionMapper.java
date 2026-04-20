package es.kitti.adoption.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.adoption.dto.*;
import es.kitti.adoption.entity.*;

@ApplicationScoped
public class AdoptionMapper {

    public AdoptionRequest toEntity(AdoptionRequestCreateRequest request, Long adopterId, String adopterEmail) {
        AdoptionRequest entity = new AdoptionRequest();
        entity.catId = request.catId();
        entity.adopterId = adopterId;
        entity.organizationId = request.organizationId();
        entity.adopterEmail = adopterEmail;
        return entity;
    }

    public AdoptionRequestResponse toResponse(AdoptionRequest entity) {
        return new AdoptionRequestResponse(
                entity.id,
                entity.catId,
                entity.adopterId,
                entity.organizationId,
                entity.status,
                entity.notes,
                entity.rejectionReason,
                entity.adopterEmail,
                entity.createdAt,
                entity.updatedAt
        );
    }

    public AdoptionRequestForm toEntity(AdoptionRequestFormCreateRequest request, Long adoptionRequestId) {
        AdoptionRequestForm entity = new AdoptionRequestForm();
        entity.adoptionRequestId = adoptionRequestId;
        entity.hasPreviousCatExperience = request.hasPreviousCatExperience();
        entity.previousPetsHistory = request.previousPetsHistory();
        entity.adultsInHousehold = request.adultsInHousehold();
        entity.hasChildren = request.hasChildren();
        entity.childrenAges = request.childrenAges();
        entity.hasOtherPets = request.hasOtherPets();
        entity.otherPetsDescription = request.otherPetsDescription();
        entity.hoursAlonePerDay = request.hoursAlonePerDay();
        entity.stableHousing = request.stableHousing();
        entity.housingInstabilityReason = request.housingInstabilityReason();
        entity.housingType = request.housingType();
        entity.housingSize = request.housingSize();
        entity.hasOutdoorAccess = request.hasOutdoorAccess();
        entity.isRental = request.isRental();
        entity.rentalPetsAllowed = request.rentalPetsAllowed();
        entity.hasWindowsWithView = request.hasWindowsWithView();
        entity.hasVerticalSpace = request.hasVerticalSpace();
        entity.hasHidingSpots = request.hasHidingSpots();
        entity.householdActivityLevel = request.householdActivityLevel();
        entity.whyCatsNeedToPlay = request.whyCatsNeedToPlay();
        entity.dailyPlayMinutes = request.dailyPlayMinutes();
        entity.plannedEnrichment = request.plannedEnrichment();
        entity.reactionToUnwantedBehavior = request.reactionToUnwantedBehavior();
        entity.hasScratchingPost = request.hasScratchingPost();
        entity.willingToEnrichEnvironment = request.willingToEnrichEnvironment();
        entity.motivationToAdopt = request.motivationToAdopt();
        entity.understandsLongTermCommitment = request.understandsLongTermCommitment();
        entity.hasVetBudget = request.hasVetBudget();
        entity.allHouseholdMembersAgree = request.allHouseholdMembersAgree();
        entity.anyoneHasAllergies = request.anyoneHasAllergies();
        entity.allergiesDetail = request.allergiesDetail();
        return entity;
    }

    public AdoptionRequestFormResponse toResponse(AdoptionRequestForm entity) {
        return new AdoptionRequestFormResponse(
                entity.id,
                entity.adoptionRequestId,
                entity.hasPreviousCatExperience,
                entity.previousPetsHistory,
                entity.adultsInHousehold,
                entity.hasChildren,
                entity.childrenAges,
                entity.hasOtherPets,
                entity.otherPetsDescription,
                entity.hoursAlonePerDay,
                entity.stableHousing,
                entity.housingInstabilityReason,
                entity.housingType,
                entity.housingSize,
                entity.hasOutdoorAccess,
                entity.isRental,
                entity.rentalPetsAllowed,
                entity.hasWindowsWithView,
                entity.hasVerticalSpace,
                entity.hasHidingSpots,
                entity.householdActivityLevel,
                entity.whyCatsNeedToPlay,
                entity.dailyPlayMinutes,
                entity.plannedEnrichment,
                entity.reactionToUnwantedBehavior,
                entity.hasScratchingPost,
                entity.willingToEnrichEnvironment,
                entity.motivationToAdopt,
                entity.understandsLongTermCommitment,
                entity.hasVetBudget,
                entity.allHouseholdMembersAgree,
                entity.anyoneHasAllergies,
                entity.allergiesDetail,
                entity.createdAt
        );
    }

    public Interview toEntity(InterviewCreateRequest request, Long adoptionRequestId) {
        Interview entity = new Interview();
        entity.adoptionRequestId = adoptionRequestId;
        entity.scheduledAt = request.scheduledAt();
        entity.notes = request.notes();
        return entity;
    }

    public InterviewResponse toResponse(Interview entity) {
        return new InterviewResponse(
                entity.id,
                entity.adoptionRequestId,
                entity.scheduledAt,
                entity.notes,
                entity.passed,
                entity.createdAt,
                entity.updatedAt
        );
    }

    public AdoptionForm toEntity(AdoptionFormCreateRequest request, Long adoptionRequestId) {
        AdoptionForm entity = new AdoptionForm();
        entity.adoptionRequestId = adoptionRequestId;
        entity.fullName = request.fullName();
        entity.idNumber = request.idNumber();
        entity.phone = request.phone();
        entity.address = request.address();
        entity.city = request.city();
        entity.postalCode = request.postalCode();
        entity.acceptsVetVisits = request.acceptsVetVisits();
        entity.acceptsFollowUpContact = request.acceptsFollowUpContact();
        entity.acceptsReturnIfNeeded = request.acceptsReturnIfNeeded();
        entity.acceptsTermsAndConditions = request.acceptsTermsAndConditions();
        entity.additionalNotes = request.additionalNotes();
        entity.signedAdoptionContract = false;
        return entity;
    }

    public AdoptionFormResponse toResponse(AdoptionForm entity) {
        return new AdoptionFormResponse(
                entity.id,
                entity.adoptionRequestId,
                entity.fullName,
                entity.idNumber,
                entity.phone,
                entity.address,
                entity.city,
                entity.postalCode,
                entity.acceptsVetVisits,
                entity.acceptsFollowUpContact,
                entity.acceptsReturnIfNeeded,
                entity.acceptsTermsAndConditions,
                entity.additionalNotes,
                entity.signedAdoptionContract,
                entity.contractSignedAt,
                entity.createdAt,
                entity.updatedAt
        );
    }

    public ExpenseResponse toResponse(Expense entity) {
        return new ExpenseResponse(
                entity.id,
                entity.adoptionRequestId,
                entity.concept,
                entity.amount,
                entity.recipient,
                entity.paid,
                entity.paidAt,
                entity.createdAt,
                entity.updatedAt
        );
    }
}