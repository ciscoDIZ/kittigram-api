package es.kitti.adoption.dto;

import jakarta.validation.constraints.NotNull;
import es.kitti.adoption.entity.AdoptionStatus;

public record AdoptionStatusUpdateRequest(
        @NotNull AdoptionStatus status,
        String reason
) {}