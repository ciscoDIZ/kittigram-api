package es.kitti.organization.dto;

public record UpdateOrganizationRequest(
        String name,
        String description,
        String address,
        String city,
        String region,
        String country,
        String phone,
        String email,
        String logoUrl
) {}
