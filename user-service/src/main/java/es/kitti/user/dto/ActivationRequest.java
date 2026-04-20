package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ActivationRequest(@JsonProperty("token") @NotBlank String token) {
}
