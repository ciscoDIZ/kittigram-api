package es.kitti.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank @JsonProperty("refreshToken") String refreshToken
) {}