package org.ciscoadiz.adoption.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.ciscoadiz.adoption.entity.ActivityLevel;
import org.ciscoadiz.adoption.entity.HousingType;

public record AdoptionRequestFormCreateRequest(
        // Sección 1: Perfil del adoptante
        @NotNull Boolean hasPreviousCatExperience,
        String previousPetsHistory,
        @NotNull @Min(1) Integer adultsInHousehold,
        @NotNull Boolean hasChildren,
        String childrenAges,
        @NotNull Boolean hasOtherPets,
        String otherPetsDescription,
        @NotNull @Min(0) @Max(24) Integer hoursAlonePerDay,
        @NotNull Boolean stableHousing,
        String housingInstabilityReason,

        // Sección 2: La vivienda
        @NotNull HousingType housingType,
        @NotNull @Min(1) Integer housingSize,
        @NotNull Boolean hasOutdoorAccess,
        @NotNull Boolean isRental,
        Boolean rentalPetsAllowed,
        @NotNull Boolean hasWindowsWithView,
        @NotNull Boolean hasVerticalSpace,
        @NotNull Boolean hasHidingSpots,
        @NotNull ActivityLevel householdActivityLevel,

        // Sección 3: Comportamiento felino
        @NotBlank String whyCatsNeedToPlay,
        @NotNull @Min(0) Integer dailyPlayMinutes,
        @NotBlank String plannedEnrichment,
        @NotBlank String reactionToUnwantedBehavior,
        @NotNull Boolean hasScratchingPost,
        @NotNull Boolean willingToEnrichEnvironment,

        // Sección 4: Compromiso
        @NotBlank String motivationToAdopt,
        @NotNull Boolean understandsLongTermCommitment,
        @NotNull Boolean hasVetBudget,
        @NotNull Boolean allHouseholdMembersAgree,
        @NotNull Boolean anyoneHasAllergies,
        String allergiesDetail
) {}