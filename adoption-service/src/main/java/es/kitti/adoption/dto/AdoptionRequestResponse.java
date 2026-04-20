package es.kitti.adoption.dto;

import es.kitti.adoption.entity.AdoptionStatus;

import java.time.LocalDateTime;

public record AdoptionRequestResponse(
        Long id,
        Long catId,
        Long adopterId,
        Long organizationId,
        AdoptionStatus status,
        String notes,
        String rejectionReason,
        String adopterEmail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}