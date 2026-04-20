package es.kitti.organization.dto;

import es.kitti.organization.entity.MemberRole;
import es.kitti.organization.entity.MemberStatus;

import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        Long organizationId,
        Long userId,
        MemberRole role,
        MemberStatus status,
        LocalDateTime joinedAt
) {}
