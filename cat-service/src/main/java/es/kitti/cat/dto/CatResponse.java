package es.kitti.cat.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CatResponse(
        Long id,
        String name,
        Integer age,
        String sex,
        String description,
        Boolean neutered,
        String status,
        String city,
        String region,
        String country,
        Double latitude,
        Double longitude,
        Long organizationId,
        List<CatImageResponse> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}