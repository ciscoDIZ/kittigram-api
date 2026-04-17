package org.ciscoadiz.cat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CatCreateRequest(
        @NotBlank String name,
        @NotNull @Min(0) @Max(30) Integer age,
        @NotBlank String sex,
        String description,
        Boolean neutered,
        @NotBlank String city,
        String region,
        @NotBlank String country,
        Double latitude,
        Double longitude
) {}