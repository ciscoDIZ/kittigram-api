package org.ciscoadiz.adoption.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record InterviewCreateRequest(
        @NotNull @Future LocalDateTime scheduledAt,
        String notes
) {}