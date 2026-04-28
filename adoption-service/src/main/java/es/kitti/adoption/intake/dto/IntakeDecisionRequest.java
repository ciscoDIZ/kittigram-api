package es.kitti.adoption.intake.dto;

import jakarta.validation.constraints.NotBlank;

public record IntakeDecisionRequest(
        @NotBlank String reason
) {}
