package org.ciscoadiz.adoption.dto;

public record AdoptionRequestCreateRequest(
        Long catId,
        Long organizationId
) {}