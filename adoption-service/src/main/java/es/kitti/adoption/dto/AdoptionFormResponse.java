package es.kitti.adoption.dto;

import java.time.LocalDateTime;

public record AdoptionFormResponse(
        Long id,
        Long adoptionRequestId,
        String fullName,
        String idNumber,
        String phone,
        String address,
        String city,
        String postalCode,
        Boolean acceptsVetVisits,
        Boolean acceptsFollowUpContact,
        Boolean acceptsReturnIfNeeded,
        Boolean acceptsTermsAndConditions,
        String additionalNotes,
        Boolean signedAdoptionContract,
        LocalDateTime contractSignedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}