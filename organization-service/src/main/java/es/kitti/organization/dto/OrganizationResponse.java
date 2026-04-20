package es.kitti.organization.dto;

import es.kitti.organization.entity.OrganizationPlan;
import es.kitti.organization.entity.OrganizationStatus;

import java.time.LocalDateTime;

public record OrganizationResponse(
        Long id,
        String name,
        String description,
        String address,
        String city,
        String region,
        String country,
        String phone,
        String email,
        String logoUrl,
        OrganizationStatus status,
        OrganizationPlan plan,
        int maxMembers,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
