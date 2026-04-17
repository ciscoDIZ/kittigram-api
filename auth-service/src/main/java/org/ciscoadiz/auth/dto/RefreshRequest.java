package org.ciscoadiz.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank @JsonProperty("refreshToken") String refreshToken
) {}