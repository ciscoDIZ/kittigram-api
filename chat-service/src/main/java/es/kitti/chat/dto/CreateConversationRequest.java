package es.kitti.chat.dto;

import jakarta.validation.constraints.NotNull;

public record CreateConversationRequest(
        @NotNull Long intakeRequestId,
        @NotNull Long userId,
        @NotNull Long organizationId
) {}