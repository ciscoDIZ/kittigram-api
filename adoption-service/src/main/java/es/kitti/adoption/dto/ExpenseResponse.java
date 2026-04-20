package es.kitti.adoption.dto;

import es.kitti.adoption.entity.ExpenseRecipient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseResponse(
        Long id,
        Long adoptionRequestId,
        String concept,
        BigDecimal amount,
        ExpenseRecipient recipient,
        Boolean paid,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}