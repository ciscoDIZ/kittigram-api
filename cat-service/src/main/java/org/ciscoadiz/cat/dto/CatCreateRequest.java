package org.ciscoadiz.cat.dto;

public record CatCreateRequest(
        String name,
        Integer age,
        String sex,
        String description,
        Boolean neutered,
        String city,
        String region,
        String country,
        Double latitude,
        Double longitude
) {}