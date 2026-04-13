package org.ciscoadiz.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshRequest(
        @JsonProperty("refreshToken") String refreshToken
) {}