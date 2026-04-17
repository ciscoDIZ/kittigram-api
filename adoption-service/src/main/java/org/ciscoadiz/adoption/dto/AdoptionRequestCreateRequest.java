package org.ciscoadiz.adoption.dto;

import jakarta.validation.constraints.NotNull;

public record AdoptionRequestCreateRequest(
        @NotNull Long catId,
        @NotNull Long organizationId
) {}