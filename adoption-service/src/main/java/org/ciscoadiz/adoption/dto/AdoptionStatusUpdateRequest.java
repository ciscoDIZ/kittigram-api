package org.ciscoadiz.adoption.dto;

import org.ciscoadiz.adoption.entity.AdoptionStatus;

public record AdoptionStatusUpdateRequest(
        AdoptionStatus status,
        String reason
) {}