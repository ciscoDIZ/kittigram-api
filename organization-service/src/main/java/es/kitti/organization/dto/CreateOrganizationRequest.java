package es.kitti.organization.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrganizationRequest(
        @NotBlank String name,
        String description,
        String address,
        String city,
        String region,
        String country,
        String phone,
        String email,
        String logoUrl
) {}
