package es.kitti.adoption.intake.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IntakeRequestCreateRequest(
        @NotNull Long targetOrganizationId,
        @NotBlank String catName,
        @NotNull @Min(0) @Max(30) Integer catAge,
        @NotBlank String region,
        @NotBlank String city,
        @NotNull Boolean vaccinated,
        String description
) {}
