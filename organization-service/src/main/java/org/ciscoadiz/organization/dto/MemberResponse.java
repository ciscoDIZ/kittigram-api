package org.ciscoadiz.organization.dto;

import org.ciscoadiz.organization.entity.MemberRole;
import org.ciscoadiz.organization.entity.MemberStatus;

import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        Long organizationId,
        Long userId,
        MemberRole role,
        MemberStatus status,
        LocalDateTime joinedAt
) {}
