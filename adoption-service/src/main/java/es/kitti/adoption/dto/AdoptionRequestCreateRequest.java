package es.kitti.adoption.dto;

import jakarta.validation.constraints.NotNull;

public record AdoptionRequestCreateRequest(
        @NotNull Long catId,
        @NotNull Long organizationId
) {}