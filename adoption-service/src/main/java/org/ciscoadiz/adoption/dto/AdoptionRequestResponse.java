package org.ciscoadiz.adoption.dto;

import org.ciscoadiz.adoption.entity.AdoptionStatus;

import java.time.LocalDateTime;

public record AdoptionRequestResponse(
        Long id,
        Long catId,
        Long adopterId,
        Long organizationId,
        AdoptionStatus status,
        String notes,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}