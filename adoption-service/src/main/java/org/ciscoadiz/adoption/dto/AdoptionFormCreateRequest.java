package org.ciscoadiz.adoption.dto;

public record AdoptionFormCreateRequest(
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
        String additionalNotes
) {}