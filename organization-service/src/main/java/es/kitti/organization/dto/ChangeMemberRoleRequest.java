package es.kitti.organization.dto;

import jakarta.validation.constraints.NotNull;
import es.kitti.organization.entity.MemberRole;

public record ChangeMemberRoleRequest(
        @NotNull MemberRole role
) {}
