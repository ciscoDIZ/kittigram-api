package es.kitti.adoption.intake.client;

public record OrganizationPublicMinimal(
        Long id,
        String name,
        String city,
        String region,
        String phone,
        String email
) {}
