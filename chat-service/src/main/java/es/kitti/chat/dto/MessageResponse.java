package es.kitti.chat.dto;

import es.kitti.chat.entity.SenderType;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        SenderType senderType,
        String content,
        LocalDateTime createdAt
) {}