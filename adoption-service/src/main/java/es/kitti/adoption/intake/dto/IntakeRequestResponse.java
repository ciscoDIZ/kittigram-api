package es.kitti.adoption.intake.dto;

import es.kitti.adoption.intake.entity.IntakeStatus;

import java.time.LocalDateTime;

public record IntakeRequestResponse(
        Long id,
        Long userId,
        Long targetOrganizationId,
        String catName,
        Integer catAge,
        String region,
        String city,
        Boolean vaccinated,
        String description,
        IntakeStatus status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {}
