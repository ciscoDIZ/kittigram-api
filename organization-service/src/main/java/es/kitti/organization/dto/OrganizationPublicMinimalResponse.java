package es.kitti.organization.dto;

public record OrganizationPublicMinimalResponse(
        Long id,
        String name,
        String city,
        String region,
        String phone,
        String email
) {}
