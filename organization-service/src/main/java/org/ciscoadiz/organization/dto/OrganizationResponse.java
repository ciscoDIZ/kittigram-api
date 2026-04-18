package org.ciscoadiz.organization.dto;

import org.ciscoadiz.organization.entity.OrganizationPlan;
import org.ciscoadiz.organization.entity.OrganizationStatus;

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
