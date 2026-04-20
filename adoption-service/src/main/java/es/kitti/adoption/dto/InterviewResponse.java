package es.kitti.adoption.dto;

import java.time.LocalDateTime;

public record InterviewResponse(
        Long id,
        Long adoptionRequestId,
        LocalDateTime scheduledAt,
        String notes,
        Boolean passed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}