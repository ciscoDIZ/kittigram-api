package org.ciscoadiz.organization.dto;

import jakarta.validation.constraints.NotNull;
import org.ciscoadiz.organization.entity.MemberRole;

public record InviteMemberRequest(
        @NotNull Long userId,
        @NotNull MemberRole role
) {}
