package es.kitti.adoption.dto;

import es.kitti.adoption.entity.ActivityLevel;
import es.kitti.adoption.entity.HousingType;

import java.time.LocalDateTime;

public record AdoptionRequestFormResponse(
        Long id,
        Long adoptionRequestId,
        Boolean hasPreviousCatExperience,
        String previousPetsHistory,
        Integer adultsInHousehold,
        Boolean hasChildren,
        String childrenAges,
        Boolean hasOtherPets,
        String otherPetsDescription,
        Integer hoursAlonePerDay,
        Boolean stableHousing,
        String housingInstabilityReason,
        HousingType housingType,
        Integer housingSize,
        Boolean hasOutdoorAccess,
        Boolean isRental,
        Boolean rentalPetsAllowed,
        Boolean hasWindowsWithView,
        Boolean hasVerticalSpace,
        Boolean hasHidingSpots,
        ActivityLevel householdActivityLevel,
        String whyCatsNeedToPlay,
        Integer dailyPlayMinutes,
        String plannedEnrichment,
        String reactionToUnwantedBehavior,
        Boolean hasScratchingPost,
        Boolean willingToEnrichEnvironment,
        String motivationToAdopt,
        Boolean understandsLongTermCommitment,
        Boolean hasVetBudget,
        Boolean allHouseholdMembersAgree,
        Boolean anyoneHasAllergies,
        String allergiesDetail,
        LocalDateTime createdAt
) {}