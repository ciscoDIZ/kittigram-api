package es.kitti.chat.dto;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        Long intakeRequestId,
        Long userId,
        Long organizationId,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt
) {}