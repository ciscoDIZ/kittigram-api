package es.kitti.cat.dto;

public record CatUpdateRequest(
        String name,
        Integer age,
        String description,
        Boolean neutered,
        String city,
        String region,
        String country,
        Double latitude,
        Double longitude
) {}