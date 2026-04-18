package org.ciscoadiz.organization.dto;

import jakarta.validation.constraints.NotNull;
import org.ciscoadiz.organization.entity.MemberRole;

public record ChangeMemberRoleRequest(
        @NotNull MemberRole role
) {}
