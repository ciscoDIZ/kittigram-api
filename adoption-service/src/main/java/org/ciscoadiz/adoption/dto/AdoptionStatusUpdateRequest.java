package org.ciscoadiz.adoption.dto;

import jakarta.validation.constraints.NotNull;
import org.ciscoadiz.adoption.entity.AdoptionStatus;

public record AdoptionStatusUpdateRequest(
        @NotNull AdoptionStatus status,
        String reason
) {}