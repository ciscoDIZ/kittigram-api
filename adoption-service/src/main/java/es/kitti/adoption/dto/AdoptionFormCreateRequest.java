package es.kitti.adoption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdoptionFormCreateRequest(
        @NotBlank String fullName,
        @NotBlank String idNumber,
        @NotBlank String phone,
        @NotBlank String address,
        @NotBlank String city,
        @NotBlank String postalCode,
        @NotNull Boolean acceptsVetVisits,
        @NotNull Boolean acceptsFollowUpContact,
        @NotNull Boolean acceptsReturnIfNeeded,
        @NotNull Boolean acceptsTermsAndConditions,
        String additionalNotes
) {}